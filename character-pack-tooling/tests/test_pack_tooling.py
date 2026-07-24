from __future__ import annotations

import contextlib
import io
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock

import yaml
from PIL import Image

TOOL_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_ROOT))

from build_character_pack import (  # noqa: E402
    ActionConfig,
    KTX2_IDENTIFIER,
    PackToolError,
    Toolchain,
    load_config,
)
from pack_pipeline import (  # noqa: E402
    ProcessedAction,
    TextureLayout,
    choose_grid,
    create_texture_pages,
    dilate_transparent_rgb,
    interpolate_png_sequence,
    process_actions,
)
from pack_release import (  # noqa: E402
    HOST_VALIDATOR_VERSION,
    create_deterministic_zip,
    dry_run,
    group_actions,
    validate_host_validator,
    validate_ktx2,
    validate_ktx2_transcodes,
)


def write_rgba(path: Path, color=(255, 0, 0, 255)) -> None:
    image = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    image.putpixel((4, 4), color)
    image.save(path)
    image.close()


class GridSelectionTest(unittest.TestCase):
    def test_117_frames_selects_five_by_five(self) -> None:
        layout = choose_grid(117, 336, 336)
        self.assertEqual(5, layout.columns)
        self.assertEqual(5, layout.page_count)
        self.assertEqual(1680, layout.page_width)
        self.assertEqual(14_112_000, layout.allocated_bytes)

    def test_candidate_must_fit_2016(self) -> None:
        layout = choose_grid(20, 336, 336, (6,), 2016)
        self.assertEqual(6, layout.columns)
        with self.assertRaisesRegex(Exception, "No 4/5/6"):
            choose_grid(20, 400, 400, (6,), 2016)


class FrameProcessingTest(unittest.TestCase):
    def test_24_fps_25_frames_becomes_31_frames(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sources = []
            for index in range(25):
                path = root / f"source_{index:03d}.png"
                write_rgba(path, (index, 0, 0, 255))
                sources.append(path)
            outputs = interpolate_png_sequence(sources, source_fps=24, destination=root / "out")
            self.assertEqual(31, len(outputs))
            self.assertTrue(all(path.is_file() for path in outputs))

    def test_png_interpolation_uses_premultiplied_alpha(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            transparent = root / "transparent.png"
            opaque = root / "opaque.png"
            Image.new("RGBA", (1, 1), (0, 0, 0, 0)).save(transparent)
            Image.new("RGBA", (1, 1), (255, 0, 0, 255)).save(opaque)

            outputs = interpolate_png_sequence(
                (transparent, opaque), source_fps=15, destination=root / "out"
            )
            with Image.open(outputs[1]) as middle:
                red, green, blue, alpha = middle.convert("RGBA").getpixel((0, 0))
            self.assertGreaterEqual(red, 250)
            self.assertEqual((green, blue), (0, 0))
            self.assertIn(alpha, (127, 128))

    def test_edge_dilation_preserves_alpha(self) -> None:
        image = Image.new("RGBA", (5, 5), (0, 0, 0, 0))
        image.putpixel((2, 2), (200, 20, 10, 255))
        result = dilate_transparent_rgb(image, 1)
        self.assertEqual(0, result.getpixel((2, 1))[3])
        self.assertEqual((200, 20, 10), result.getpixel((2, 1))[:3])
        image.close()
        result.close()


class FramePackingTest(unittest.TestCase):
    def test_texture_pages_preserve_rgb_in_fully_transparent_texels(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            frame = root / "frame.png"
            image = Image.new("RGBA", (8, 8), (200, 20, 10, 0))
            image.putpixel((4, 4), (200, 20, 10, 255))
            image.save(frame)
            image.close()

            config = mock.Mock()
            config.target.frame_width = 8
            config.target.frame_height = 8
            config.target.grid_candidates = (4,)
            config.target.max_texture_size = 32

            _, pages = create_texture_pages(
                "standard", (frame,), config, root / "pages"
            )
            with Image.open(pages[0]) as page:
                self.assertEqual((200, 20, 10, 0), page.convert("RGBA").getpixel((0, 0)))

    def test_shared_idle_and_open_first_frame_produces_117_total_frames(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            idle = root / "idle.png"
            open_first = root / "open_first.png"
            write_rgba(idle, (20, 200, 30, 255))
            write_rgba(open_first, (20, 200, 30, 255))
            remaining = []
            for index in range(116):
                frame = root / f"frame_{index:03d}.png"
                write_rgba(frame, ((index + 1) % 255, 20, 30, 255))
                remaining.append(frame)

            def action(action_id: str, frames: tuple[Path, ...]) -> ProcessedAction:
                return ProcessedAction(
                    ActionConfig(
                        action_id=action_id,
                        source=frames[0],
                        source_fps=None,
                        mode="once",
                        next_action=None,
                        reversible=False,
                        semantic=None,
                        event=None,
                        texture_id="standard",
                    ),
                    frames,
                )

            textures, ranges = group_actions(
                (
                    action("idle", (idle,)),
                    action("card_open", (open_first, *remaining[:30])),
                    action("card_visible", tuple(remaining[30:60])),
                    action("card_close", tuple(remaining[60:116])),
                )
            )
            self.assertEqual(117, len(textures["standard"]))
            self.assertEqual(("standard", 0, 1), ranges["idle"])
            self.assertEqual(("standard", 0, 31), ranges["card_open"])
            self.assertEqual(("standard", 31, 30), ranges["card_visible"])
            self.assertEqual(("standard", 61, 56), ranges["card_close"])


class PackageTest(unittest.TestCase):
    def test_zip_is_flat_and_deterministic(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = root / "manifest.json"
            texture = root / "standard.ktx2"
            manifest.write_text('{"schemaVersion":2}\n', encoding="utf-8")
            texture.write_bytes(b"ktx2")
            first = root / "first.zip"
            second = root / "second.zip"
            create_deterministic_zip(first, manifest, {"standard": texture})
            create_deterministic_zip(second, manifest, {"standard": texture})
            self.assertEqual(first.read_bytes(), second.read_bytes())
            with zipfile.ZipFile(first) as archive:
                self.assertEqual(["manifest.json", "standard.ktx2"], archive.namelist())

    def test_ktx2_header_and_ranges_are_validated(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "standard.ktx2"
            header = KTX2_IDENTIFIER + struct.pack(
                "<13I2Q",
                0, 1, 16, 16, 0, 5, 1, 1, 2,
                104, 4, 0, 0, 0, 0,
            )
            level_index = struct.pack("<3Q", 108, 16, 32)
            path.write_bytes(header + level_index + b"DFD!" + b"x" * 16)
            layout = TextureLayout("standard", 117, 5, 5, 5, 16, 16, 1280)
            values = validate_ktx2(path, layout)
            self.assertEqual(5, values["layerCount"])

    def test_host_validator_version_is_pinned(self) -> None:
        with mock.patch("pack_release.run_command", return_value=HOST_VALIDATOR_VERSION) as runner:
            validate_host_validator("validator")
        runner.assert_called_once_with(["validator", "--version"])

    def test_all_pages_are_sent_to_the_host_transcoder(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "standard.ktx2"
            path.write_bytes(b"ktx2")
            layout = TextureLayout("standard", 117, 5, 5, 5, 1680, 1680, 0)
            with mock.patch("pack_release.run_command") as runner:
                validate_ktx2_transcodes(path, layout, "validator")
            runner.assert_called_once_with(
                [
                    "validator",
                    str(path.resolve()),
                    "1680",
                    "1680",
                    "5",
                ]
            )

    def test_host_transcoder_is_mandatory(self) -> None:
        layout = TextureLayout("standard", 1, 1, 1, 1, 336, 336, 0)
        with self.assertRaisesRegex(PackToolError, "host validator"):
            validate_ktx2_transcodes(Path("standard.ktx2"), layout, None)


class DryRunTest(unittest.TestCase):
    def test_dry_run_does_not_require_external_tools(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            actions = {}
            for action_id, mode in (
                ("idle", "hold_last"),
                ("card_open", "once"),
                ("card_visible", "loop"),
                ("card_close", "once"),
            ):
                source_dir = root / action_id
                source_dir.mkdir()
                write_rgba(source_dir / "0001.png")
                actions[action_id] = {
                    "source": action_id,
                    "sourceFps": 30,
                    "mode": mode,
                    "semantic": action_id,
                }
            actions["card_open"]["reversible"] = True
            actions["card_open"]["next"] = "card_visible"
            actions["card_close"]["reversible"] = True
            actions["card_close"]["next"] = "idle"
            config_path = root / "pack.yaml"
            config_path.write_text(
                yaml.safe_dump(
                    {
                        "packId": "test_pet",
                        "packVersion": 2,
                        "actions": actions,
                    },
                    sort_keys=False,
                ),
                encoding="utf-8",
            )
            config = load_config(config_path)
            with contextlib.redirect_stdout(io.StringIO()):
                processed = process_actions(
                    config,
                    root / "work",
                    Toolchain("toktx", "unused", None, None),
                )
            textures, ranges = group_actions(processed)
            self.assertEqual(1, len(textures["standard"]))
            self.assertEqual(("standard", 0, 1), ranges["idle"])
            self.assertEqual(("standard", 0, 1), ranges["card_open"])

            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                dry_run(config)
            self.assertIn("No files will be generated", output.getvalue())
            self.assertIn("texture standard", output.getvalue())


if __name__ == "__main__":
    unittest.main()

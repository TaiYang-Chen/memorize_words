"""KTX2 encoding, schema-v2 manifest creation, and deterministic release packaging."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
import struct
import sys
import tempfile
import zipfile
from collections import OrderedDict
from pathlib import Path
from typing import Any, Mapping, Sequence

from PIL import Image

from build_character_pack import (
    KTX2_IDENTIFIER,
    TARGET_FPS,
    BuildConfig,
    PackToolError,
    Toolchain,
    is_video_source,
    load_config,
    png_sources,
    resolve_toolchain,
    run_command,
    validate_inputs,
)
from pack_pipeline import (
    ProcessedAction,
    TextureLayout,
    choose_grid,
    create_texture_pages,
    process_actions,
)


MAX_STANDARD_GPU_BYTES = 16 * 1024 * 1024
MAX_TEXTURE_GPU_BYTES = 16 * 1024 * 1024
MAX_RUNTIME_GPU_BYTES = 32 * 1024 * 1024
MAX_PACKAGE_BYTES = 25 * 1024 * 1024
MAX_PACKAGE_ENTRIES = 16
MAX_TEXTURE_COUNT = MAX_PACKAGE_ENTRIES - 1
MAX_TEXTURE_FRAMES = 256
MAX_TEXTURE_PAGES = 16
KTX2_HEADER_SIZE = 80
KTX2_LEVEL_INDEX_SIZE = 24
HOST_VALIDATOR_VERSION = (
    "basis_ktx2_validator 1 basisu 5f6a0a0ca66c34e1dad6da7ce43d1d34ca8fef4d"
)


def _format_mib(byte_count: int) -> str:
    return f"{byte_count / (1024 * 1024):.2f} MiB"


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def encode_ktx2(
    layout: TextureLayout,
    pages: Sequence[Path],
    output: Path,
    config: BuildConfig,
    toolchain: Toolchain,
) -> None:
    if len(pages) != layout.page_count:
        raise PackToolError(
            f"Texture {layout.texture_id} expected {layout.page_count} pages, got {len(pages)}"
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    if toolchain.encoder_kind == "toktx":
        command = [
            toolchain.encoder_path,
            "--t2",
            "--encode",
            "uastc",
            "--uastc_quality",
            str(config.encoder.uastc_quality),
            "--zcmp",
            str(config.encoder.zstd_level),
            "--assign_oetf",
            "srgb",
            "--assign_primaries",
            "bt709",
            "--layers",
            str(layout.page_count),
            *config.encoder.extra_args,
            str(output),
            *(str(page) for page in pages),
        ]
        run_command(command)
    elif toolchain.encoder_kind == "basisu":
        # BasisU's multifile mode creates one 2D-array layer from each page_XXXX.png.
        expected_names = [f"page_{index:04d}.png" for index in range(len(pages))]
        if [page.name for page in pages] != expected_names or len({page.parent for page in pages}) != 1:
            raise PackToolError("BasisU pages must be a contiguous page_XXXX.png sequence")
        command = [
            toolchain.encoder_path,
            "-ktx2",
            "-uastc",
            "-uastc_level",
            str(config.encoder.uastc_quality),
            "-ktx2_zstandard_level",
            str(config.encoder.zstd_level),
            "-tex_type",
            "2darray",
            "-multifile_printf",
            "page_%04u.png",
            "-multifile_first",
            "0",
            "-multifile_num",
            str(layout.page_count),
            *config.encoder.extra_args,
            "-output_file",
            str(output.resolve()),
        ]
        run_command(command, cwd=pages[0].parent)
    else:  # pragma: no cover - resolve_toolchain prevents this
        raise PackToolError(f"Unsupported encoder: {toolchain.encoder_kind}")
    validate_ktx2(output, layout)
    validate_ktx2_transcodes(output, layout, toolchain.validator_path)


def validate_host_validator(executable: str | None) -> None:
    if not executable:
        raise PackToolError("Missing Basis KTX2 host validator")
    version = run_command([executable, "--version"]).strip()
    if version != HOST_VALIDATOR_VERSION:
        raise PackToolError(
            "Basis KTX2 host validator version mismatch: "
            f"{version or '<empty>'} (expected {HOST_VALIDATOR_VERSION})"
        )


def validate_ktx2(path: Path, expected: TextureLayout) -> Mapping[str, int]:
    try:
        data = path.read_bytes()
    except OSError as exc:
        raise PackToolError(f"Cannot read encoded KTX2 {path}: {exc}") from exc
    if len(data) < KTX2_HEADER_SIZE or data[:12] != KTX2_IDENTIFIER:
        raise PackToolError(f"Encoder did not produce a valid KTX2 file: {path}")
    values = struct.unpack_from("<13I2Q", data, 12)
    (
        vk_format,
        type_size,
        pixel_width,
        pixel_height,
        pixel_depth,
        layer_count,
        face_count,
        level_count,
        supercompression,
        dfd_offset,
        dfd_length,
        kvd_offset,
        kvd_length,
        sgd_offset,
        sgd_length,
    ) = values
    effective_layers = layer_count if layer_count != 0 else 1
    checks = {
        "vkFormat": (vk_format, 0),
        "typeSize": (type_size, 1),
        "pixelWidth": (pixel_width, expected.page_width),
        "pixelHeight": (pixel_height, expected.page_height),
        "pixelDepth": (pixel_depth, 0),
        "layerCount": (effective_layers, expected.page_count),
        "faceCount": (face_count, 1),
        "levelCount": (level_count, 1),
        "supercompressionScheme": (supercompression, 2),  # KTX_SS_ZSTD
    }
    mismatches = [
        f"{name}={actual} (expected {wanted})"
        for name, (actual, wanted) in checks.items()
        if actual != wanted
    ]
    if mismatches:
        raise PackToolError(f"KTX2 header mismatch for {path.name}: " + ", ".join(mismatches))

    def require_range(label: str, offset: int, length: int, allow_empty: bool = False) -> None:
        if length == 0 and allow_empty:
            return
        if length <= 0 or offset < KTX2_HEADER_SIZE or offset + length > len(data):
            raise PackToolError(f"KTX2 {label} range is invalid in {path.name}")

    require_range("DFD", dfd_offset, dfd_length)
    require_range("KVD", kvd_offset, kvd_length, allow_empty=True)
    require_range("SGD", sgd_offset, sgd_length, allow_empty=True)
    level_index_end = KTX2_HEADER_SIZE + level_count * KTX2_LEVEL_INDEX_SIZE
    if level_index_end > len(data):
        raise PackToolError(f"KTX2 level index is truncated in {path.name}")
    for level in range(level_count):
        byte_offset, byte_length, uncompressed_length = struct.unpack_from(
            "<3Q", data, KTX2_HEADER_SIZE + level * KTX2_LEVEL_INDEX_SIZE
        )
        if byte_length <= 0 or uncompressed_length <= 0:
            raise PackToolError(f"KTX2 level {level} is empty in {path.name}")
        if byte_offset < level_index_end or byte_offset + byte_length > len(data):
            raise PackToolError(f"KTX2 level {level} range is invalid in {path.name}")
    return {
        "pixelWidth": pixel_width,
        "pixelHeight": pixel_height,
        "layerCount": effective_layers,
        "levelCount": level_count,
        "supercompressionScheme": supercompression,
    }


def validate_ktx2_transcodes(
    path: Path,
    expected: TextureLayout,
    executable: str | None,
) -> None:
    if not executable:
        raise PackToolError("Missing Basis KTX2 host validator")
    run_command(
        [
            executable,
            str(path.resolve()),
            str(expected.page_width),
            str(expected.page_height),
            str(expected.page_count),
        ]
    )


def _frame_digest(path: Path, cache: dict[Path, bytes]) -> bytes:
    digest = cache.get(path)
    if digest is None:
        digest = hashlib.sha256(path.read_bytes()).digest()
        cache[path] = digest
    return digest


def _find_subsequence(haystack: Sequence[bytes], needle: Sequence[bytes]) -> int | None:
    if not needle or len(needle) > len(haystack):
        return None
    wanted = list(needle)
    for start in range(len(haystack) - len(wanted) + 1):
        if list(haystack[start : start + len(wanted)]) == wanted:
            return start
    return None


def _suffix_prefix_overlap(existing: Sequence[bytes], incoming: Sequence[bytes]) -> int:
    maximum = min(len(existing), len(incoming))
    for length in range(maximum, 0, -1):
        if list(existing[-length:]) == list(incoming[:length]):
            return length
    return 0


def group_actions(
    processed: Sequence[ProcessedAction],
) -> tuple[
    OrderedDict[str, list[Path]],
    dict[str, tuple[str, int, int]],
]:
    textures: OrderedDict[str, list[Path]] = OrderedDict()
    texture_digests: dict[str, list[bytes]] = {}
    digest_cache: dict[Path, bytes] = {}
    clip_ranges: dict[str, tuple[str, int, int]] = {}
    for action in processed:
        frames = textures.setdefault(action.config.texture_id, [])
        existing_digests = texture_digests.setdefault(action.config.texture_id, [])
        action_digests = [
            _frame_digest(frame, digest_cache) for frame in action.frames
        ]
        existing_start = _find_subsequence(existing_digests, action_digests)
        if existing_start is not None:
            start = existing_start
        else:
            overlap = _suffix_prefix_overlap(existing_digests, action_digests)
            start = len(frames) - overlap
            frames.extend(action.frames[overlap:])
            existing_digests.extend(action_digests[overlap:])
        clip_ranges[action.config.action_id] = (
            action.config.texture_id,
            start,
            len(action.frames),
        )
    return textures, clip_ranges


def build_manifest(
    config: BuildConfig,
    layouts: Mapping[str, TextureLayout],
    clip_ranges: Mapping[str, tuple[str, int, int]],
) -> OrderedDict[str, Any]:
    textures: OrderedDict[str, Any] = OrderedDict()
    for texture_id, layout in layouts.items():
        textures[texture_id] = OrderedDict(
            (
                ("type", "ktx2_paged"),
                ("file", f"{texture_id}.ktx2"),
                ("frameWidth", config.target.frame_width),
                ("frameHeight", config.target.frame_height),
                ("pageWidth", layout.page_width),
                ("pageHeight", layout.page_height),
                ("columns", layout.columns),
                ("rows", layout.rows),
                ("pageCount", layout.page_count),
                ("frameCount", layout.frame_count),
                ("basisMode", "uastc"),
                ("colorSpace", "srgb"),
                ("alphaMode", "straight"),
            )
        )

    clips: OrderedDict[str, Any] = OrderedDict()
    semantic_bindings: OrderedDict[str, str] = OrderedDict()
    event_bindings: OrderedDict[str, str] = OrderedDict()
    for action in config.actions:
        texture_id, start_frame, frame_count = clip_ranges[action.action_id]
        clip = OrderedDict(
            (
                ("texture", texture_id),
                ("startFrame", start_frame),
                ("frameCount", frame_count),
                ("fps", TARGET_FPS),
                ("mode", action.mode),
            )
        )
        if action.next_action is not None:
            clip["next"] = action.next_action
        if action.reversible:
            clip["reversible"] = True
        clips[action.action_id] = clip
        if action.semantic is not None:
            semantic_bindings[action.semantic] = action.action_id
        if action.event is not None:
            event_bindings[action.event] = action.action_id

    return OrderedDict(
        (
            ("schemaVersion", 2),
            ("packId", config.pack_id),
            ("packVersion", config.pack_version),
            ("fallbackClip", config.fallback_clip),
            ("textures", textures),
            ("clips", clips),
            ("semanticBindings", semantic_bindings),
            ("eventBindings", event_bindings),
        )
    )


def _write_json(path: Path, payload: Mapping[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def create_deterministic_zip(
    output: Path,
    manifest_path: Path,
    texture_paths: Mapping[str, Path],
) -> None:
    entries = [("manifest.json", manifest_path)] + sorted(
        ((path.name, path) for path in texture_paths.values()), key=lambda item: item[0]
    )
    if len(entries) > MAX_PACKAGE_ENTRIES:
        raise PackToolError(
            f"Package has {len(entries)} entries; schema-v2 packages allow at most {MAX_PACKAGE_ENTRIES}"
        )
    if len({name for name, _ in entries}) != len(entries):
        raise PackToolError("Package contains duplicate flat entry names")
    with zipfile.ZipFile(output, "w", allowZip64=False) as archive:
        for name, source in entries:
            compression = zipfile.ZIP_DEFLATED if name == "manifest.json" else zipfile.ZIP_STORED
            archive.writestr(_zip_info(name), source.read_bytes(), compress_type=compression)


def write_preview(config: BuildConfig, processed: Sequence[ProcessedAction], output: Path) -> None:
    action = next(
        item for item in processed if item.config.action_id == config.preview.action_id
    )
    if config.preview.frame >= len(action.frames):
        raise PackToolError(
            f"preview.frame={config.preview.frame} exceeds {config.preview.action_id} "
            f"frame count {len(action.frames)}"
        )
    with Image.open(action.frames[config.preview.frame]) as image:
        image.convert("RGBA").save(output, format="PNG", compress_level=9)


def _assert_standard_texture(config: BuildConfig) -> None:
    misplaced = [
        action.action_id
        for action in config.actions
        if action.action_id in {"idle", "card_open", "card_visible", "card_close"}
        and action.texture_id != "standard"
    ]
    if misplaced:
        raise PackToolError(
            "Required resident actions must use texture 'standard': " + ", ".join(misplaced)
        )


def build_release(
    config: BuildConfig,
    toolchain: Toolchain,
    output_root: Path,
    force: bool,
    keep_work: bool,
) -> Path:
    _assert_standard_texture(config)
    validate_host_validator(toolchain.validator_path)
    warnings = validate_inputs(config, toolchain)
    for warning in warnings:
        print(f"[warning] {warning}")

    final_dir = output_root.resolve() / f"{config.pack_id}_v{config.pack_version}"
    if final_dir.exists() and not force:
        raise PackToolError(f"Output already exists: {final_dir} (use --force to replace it)")
    output_root.resolve().mkdir(parents=True, exist_ok=True)

    temporary = tempfile.TemporaryDirectory(prefix=f"{config.pack_id}-v{config.pack_version}-")
    work_root = Path(temporary.name)
    succeeded = False
    try:
        processed = process_actions(config, work_root, toolchain)
        texture_frames, clip_ranges = group_actions(processed)
        layouts: OrderedDict[str, TextureLayout] = OrderedDict()
        page_sets: dict[str, tuple[Path, ...]] = {}
        for texture_id, frames in texture_frames.items():
            layout, pages = create_texture_pages(
                texture_id, frames, config, work_root / "pages"
            )
            layouts[texture_id] = layout
            page_sets[texture_id] = pages
            print(
                f"[pages] {texture_id}: {layout.frame_count} frames, "
                f"{layout.columns}x{layout.rows}, {layout.page_count} pages, "
                f"{layout.page_width}x{layout.page_height}, {_format_mib(layout.allocated_bytes)}"
            )
        if len(layouts) > MAX_TEXTURE_COUNT:
            raise PackToolError(
                f"Pack has {len(layouts)} textures; clients support at most {MAX_TEXTURE_COUNT}"
            )
        for texture_id, layout in layouts.items():
            if layout.frame_count > MAX_TEXTURE_FRAMES:
                raise PackToolError(
                    f"Texture {texture_id} has {layout.frame_count} frames; maximum is {MAX_TEXTURE_FRAMES}"
                )
            if layout.page_count > MAX_TEXTURE_PAGES:
                raise PackToolError(
                    f"Texture {texture_id} has {layout.page_count} pages; maximum is {MAX_TEXTURE_PAGES}"
                )
            if layout.allocated_bytes > MAX_TEXTURE_GPU_BYTES:
                raise PackToolError(
                    f"Texture {texture_id} needs {_format_mib(layout.allocated_bytes)}, exceeding "
                    f"the {_format_mib(MAX_TEXTURE_GPU_BYTES)} per-texture GPU budget"
                )
        standard = layouts.get("standard")
        if standard is None:
            raise PackToolError("Texture 'standard' was not generated")
        if standard.allocated_bytes > MAX_STANDARD_GPU_BYTES:
            raise PackToolError(
                f"Standard texture needs {_format_mib(standard.allocated_bytes)}, exceeding "
                f"the {_format_mib(MAX_STANDARD_GPU_BYTES)} resident GPU budget"
            )
        largest_optional = max(
            (layout.allocated_bytes for key, layout in layouts.items() if key != "standard"),
            default=0,
        )
        if standard.allocated_bytes + largest_optional > MAX_RUNTIME_GPU_BYTES:
            raise PackToolError(
                "Standard plus the largest optional texture exceeds the 32 MiB runtime budget"
            )

        staging = work_root / "release"
        staging.mkdir(parents=True)
        encoded: OrderedDict[str, Path] = OrderedDict()
        for texture_id, layout in layouts.items():
            output = staging / f"{texture_id}.ktx2"
            print(f"[encode] {texture_id} with {toolchain.encoder_kind}")
            encode_ktx2(layout, page_sets[texture_id], output, config, toolchain)
            encoded[texture_id] = output

        manifest = build_manifest(config, layouts, clip_ranges)
        manifest_path = staging / "manifest.json"
        _write_json(manifest_path, manifest)
        preview_path = staging / f"{config.pack_id}_preview.png"
        write_preview(config, processed, preview_path)
        zip_path = staging / f"{config.pack_id}_v{config.pack_version}.zip"
        create_deterministic_zip(zip_path, manifest_path, encoded)
        if zip_path.stat().st_size > MAX_PACKAGE_BYTES:
            raise PackToolError(
                f"Package is {_format_mib(zip_path.stat().st_size)}, exceeding the "
                f"{_format_mib(MAX_PACKAGE_BYTES)} client download limit"
            )

        artifact_paths = [manifest_path, *encoded.values(), preview_path, zip_path]
        checksums = OrderedDict(
            (path.name, {"sha256": _sha256_file(path), "size": path.stat().st_size})
            for path in sorted(artifact_paths, key=lambda item: item.name)
        )
        metadata = OrderedDict(
            (
                ("packId", config.pack_id),
                ("packVersion", config.pack_version),
                ("hostValidator", HOST_VALIDATOR_VERSION),
                ("schemaVersion", 2),
                ("encoder", toolchain.encoder_kind),
                (
                    "textures",
                    OrderedDict(
                        (
                            texture_id,
                            OrderedDict(
                                (
                                    ("frameCount", layout.frame_count),
                                    ("grid", f"{layout.columns}x{layout.rows}"),
                                    ("pageCount", layout.page_count),
                                    ("pageWidth", layout.page_width),
                                    ("pageHeight", layout.page_height),
                                    ("estimatedGpuBytes", layout.allocated_bytes),
                                )
                            ),
                        )
                        for texture_id, layout in layouts.items()
                    ),
                ),
                ("artifacts", checksums),
            )
        )
        metadata_path = staging / "build-metadata.json"
        _write_json(metadata_path, metadata)
        checksum_paths = sorted([*artifact_paths, metadata_path], key=lambda item: item.name)
        (staging / "SHA256SUMS.txt").write_text(
            "".join(f"{_sha256_file(path)}  {path.name}\n" for path in checksum_paths),
            encoding="ascii",
            newline="\n",
        )

        if final_dir.exists():
            shutil.rmtree(final_dir)
        shutil.copytree(staging, final_dir)
        if keep_work:
            debug_dir = output_root.resolve() / f"{config.pack_id}_v{config.pack_version}_work"
            if debug_dir.exists():
                shutil.rmtree(debug_dir)
            shutil.copytree(work_root / "normalized", debug_dir / "normalized")
            shutil.copytree(work_root / "pages", debug_dir / "pages")
        succeeded = True
        print(f"[done] {final_dir}")
        print(f"[sha256] {_sha256_file(final_dir / zip_path.name)}  {zip_path.name}")
        return final_dir
    finally:
        # TemporaryDirectory cleanup may fail on Windows if an external encoder still has a handle.
        try:
            temporary.cleanup()
        except OSError as exc:
            if succeeded:
                print(f"[warning] temporary work directory could not be removed: {exc}")
            else:
                raise


def strict_check(config: BuildConfig, toolchain: Toolchain) -> None:
    _assert_standard_texture(config)
    validate_host_validator(toolchain.validator_path)
    warnings = validate_inputs(config, toolchain)
    print(f"[ok] configuration: {config.config_path}")
    print(f"[ok] encoder: {toolchain.encoder_kind} ({toolchain.encoder_path})")
    print(f"[ok] host validator: {toolchain.validator_path}")
    if toolchain.ffmpeg_path:
        print(f"[ok] ffmpeg: {toolchain.ffmpeg_path}")
        print(f"[ok] ffprobe: {toolchain.ffprobe_path}")
    for warning in warnings:
        print(f"[warning] {warning}")
    print("[ok] inputs passed preflight validation")


def dry_run(config: BuildConfig) -> None:
    _assert_standard_texture(config)
    print(f"Pack: {config.pack_id} v{config.pack_version}, schema 2")
    print("No files will be generated and no external program will be executed.")
    print("Dependencies:")
    for label, names in (
        ("ffmpeg", ("ffmpeg", "ffmpeg.exe")),
        ("ffprobe", ("ffprobe", "ffprobe.exe")),
        ("toktx", ("toktx", "toktx.exe")),
        ("basisu", ("basisu", "basisu.exe")),
        ("basis_ktx2_validator", ("basis_ktx2_validator", "basis_ktx2_validator.exe")),
    ):
        found = next((shutil.which(name) for name in names if shutil.which(name)), None)
        print(f"  {label}: {found or 'MISSING (required only when used)'}")

    totals: OrderedDict[str, int | None] = OrderedDict()
    for action in config.actions:
        frame_count: int | None
        if is_video_source(action.source):
            frame_count = None
            detail = "video; final count determined by FFmpeg at 30 FPS"
        else:
            frames = png_sources(action.source)
            source_fps = float(action.source_fps or TARGET_FPS)
            frame_count = max(1, round((len(frames) - 1) * TARGET_FPS / source_fps) + 1)
            detail = f"{len(frames)} PNGs at {source_fps:g} FPS -> {frame_count} frames"
        previous = totals.get(action.texture_id, 0)
        totals[action.texture_id] = (
            None if previous is None or frame_count is None else previous + frame_count
        )
        print(f"  {action.action_id}: {detail}; texture={action.texture_id}")
    for texture_id, total in totals.items():
        if total is None:
            print(f"  texture {texture_id}: layout unknown until video extraction")
            continue
        layout = choose_grid(
            total,
            config.target.frame_width,
            config.target.frame_height,
            config.target.grid_candidates,
            config.target.max_texture_size,
        )
        print(
            f"  texture {texture_id}: {total} frames -> {layout.columns}x{layout.rows}, "
            f"{layout.page_count} pages, {_format_mib(layout.allocated_bytes)}"
        )


def make_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build a schema-v2 KTX2/Basis GPU character pack from videos or PNG frames."
    )
    parser.add_argument("config", type=Path, help="Path to pack.yaml")
    parser.add_argument("--output", type=Path, default=Path("dist"), help="Output root")
    parser.add_argument("--encoder", choices=("auto", "toktx", "basisu"))
    parser.add_argument("--encoder-path", help="Explicit path to toktx or basisu")
    parser.add_argument("--validator-path", help="Explicit path to basis_ktx2_validator")
    parser.add_argument("--ffmpeg", help="Explicit path to ffmpeg")
    parser.add_argument("--ffprobe", help="Explicit path to ffprobe")
    parser.add_argument("--force", action="store_true", help="Replace an existing output directory")
    parser.add_argument("--keep-work", action="store_true", help="Keep normalized frames and PNG pages")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Strictly validate inputs and dependencies")
    mode.add_argument(
        "--dry-run",
        action="store_true",
        help="Inspect config/layout without invoking FFmpeg or a KTX2 encoder",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = make_parser()
    args = parser.parse_args(argv)
    try:
        config = load_config(args.config)
        if args.dry_run:
            dry_run(config)
            return 0
        toolchain = resolve_toolchain(
            config,
            encoder_override=args.encoder,
            encoder_path=args.encoder_path,
            ffmpeg_path=args.ffmpeg,
            ffprobe_path=args.ffprobe,
            validator_path=args.validator_path,
        )
        if args.check:
            strict_check(config, toolchain)
            return 0
        build_release(config, toolchain, args.output, args.force, args.keep_work)
        return 0
    except PackToolError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    except KeyboardInterrupt:
        print("error: interrupted", file=sys.stderr)
        return 130


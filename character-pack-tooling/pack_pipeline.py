"""Frame normalization, KTX2 encoding, and release artifact generation."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from fractions import Fraction
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from PIL import Image, ImageChops

from build_character_pack import (
    DEFAULT_GRID_CANDIDATES,
    DEFAULT_MAX_TEXTURE_SIZE,
    KTX2_IDENTIFIER,
    TARGET_FPS,
    ActionConfig,
    BuildConfig,
    PackToolError,
    Toolchain,
    is_video_source,
    load_config,
    natural_sort_key,
    png_sources,
    probe_video,
    resolve_toolchain,
    run_command,
    validate_inputs,
    video_frame_rate,
)


@dataclass(frozen=True)
class ProcessedAction:
    config: ActionConfig
    frames: tuple[Path, ...]


@dataclass(frozen=True)
class TextureLayout:
    texture_id: str
    frame_count: int
    columns: int
    rows: int
    page_count: int
    page_width: int
    page_height: int
    allocated_bytes: int


def extract_video_frames(
    action: ActionConfig,
    destination: Path,
    toolchain: Toolchain,
) -> tuple[Path, ...]:
    assert toolchain.ffmpeg_path is not None
    assert toolchain.ffprobe_path is not None
    stream = probe_video(toolchain.ffprobe_path, action.source)
    source_fps = video_frame_rate(stream, action.source)
    destination.mkdir(parents=True, exist_ok=True)
    source_directory = destination / "source"
    source_directory.mkdir(parents=True, exist_ok=True)
    output_pattern = source_directory / "frame_%06d.png"
    command = (
        toolchain.ffmpeg_path,
        "-hide_banner",
        "-loglevel",
        "warning",
        "-y",
        "-i",
        str(action.source),
        "-map",
        "0:v:0",
        "-an",
        "-sn",
        "-dn",
        "-vf",
        "format=rgba",
        "-vsync",
        "0",
        "-start_number",
        "0",
        str(output_pattern),
    )
    run_command(command)
    source_frames = tuple(
        sorted(source_directory.glob("frame_*.png"), key=natural_sort_key)
    )
    if not source_frames:
        raise PackToolError(f"FFmpeg produced no frames for action {action.action_id}")
    # FFmpeg's minterpolate path does not reliably retain alpha on all supported builds. Decode
    # source RGBA first, then perform deterministic premultiplied-alpha temporal interpolation.
    return interpolate_png_sequence(
        source_frames,
        source_fps=source_fps,
        destination=destination / "fps30",
    )


def interpolate_png_sequence(
    source_frames: Sequence[Path],
    source_fps: Fraction,
    destination: Path,
) -> tuple[Path, ...]:
    destination.mkdir(parents=True, exist_ok=True)
    if not source_frames:
        raise PackToolError("Cannot interpolate an empty PNG sequence")
    if len(source_frames) == 1 or source_fps == TARGET_FPS:
        outputs: list[Path] = []
        for index, source in enumerate(source_frames):
            output = destination / f"frame_{index:06d}.png"
            with Image.open(source) as image:
                image.convert("RGBA").save(output, format="PNG", compress_level=6)
            outputs.append(output)
        return tuple(outputs)

    images: list[Image.Image] = []
    expected_size: tuple[int, int] | None = None
    try:
        for frame in source_frames:
            with Image.open(frame) as image:
                rgba = image.convert("RGBA")
                rgba.load()
            if expected_size is None:
                expected_size = rgba.size
            elif rgba.size != expected_size:
                raise PackToolError(f"Cannot interpolate mixed dimensions in {frame.parent}")
            images.append(rgba)

        output_count = max(
            1,
            int(round((len(images) - 1) * TARGET_FPS / float(source_fps))) + 1,
        )
        outputs = []
        for output_index in range(output_count):
            source_position = output_index * float(source_fps) / TARGET_FPS
            lower_index = min(int(math.floor(source_position)), len(images) - 1)
            upper_index = min(lower_index + 1, len(images) - 1)
            blend = source_position - lower_index
            if upper_index == lower_index or blend <= 1e-9:
                frame_image = images[lower_index].copy()
            else:
                # Pillow's RGBa mode stores premultiplied color. Interpolating in this mode
                # avoids dark RGB fringes when an opaque pixel moves into transparent space.
                lower_premultiplied = images[lower_index].convert("RGBa")
                upper_premultiplied = images[upper_index].convert("RGBa")
                blended_premultiplied = Image.blend(
                    lower_premultiplied,
                    upper_premultiplied,
                    blend,
                )
                frame_image = blended_premultiplied.convert("RGBA")
                lower_premultiplied.close()
                upper_premultiplied.close()
                blended_premultiplied.close()
            output = destination / f"frame_{output_index:06d}.png"
            frame_image.save(output, format="PNG", compress_level=6)
            frame_image.close()
            outputs.append(output)
        return tuple(outputs)
    finally:
        for image in images:
            image.close()


def temporal_frames(
    action: ActionConfig,
    destination: Path,
    toolchain: Toolchain,
) -> tuple[Path, ...]:
    if is_video_source(action.source):
        return extract_video_frames(action, destination, toolchain)
    return interpolate_png_sequence(
        png_sources(action.source),
        action.source_fps or Fraction(TARGET_FPS, 1),
        destination,
    )


def _shift_image(image: Image.Image, dx: int, dy: int, fill: int | tuple[int, ...]) -> Image.Image:
    width, height = image.size
    shifted = Image.new(image.mode, image.size, fill)
    source_left = max(0, -dx)
    source_top = max(0, -dy)
    source_right = min(width, width - dx)
    source_bottom = min(height, height - dy)
    if source_right <= source_left or source_bottom <= source_top:
        return shifted
    crop = image.crop((source_left, source_top, source_right, source_bottom))
    shifted.paste(crop, (source_left + dx, source_top + dy))
    crop.close()
    return shifted


def dilate_transparent_rgb(image: Image.Image, radius: int) -> Image.Image:
    if radius <= 0:
        return image.copy()
    rgba = image.convert("RGBA")
    alpha = rgba.getchannel("A")
    known = alpha.point(lambda value: 255 if value > 0 else 0)
    rgb = rgba.convert("RGB")
    directions = (
        (-1, 0), (1, 0), (0, -1), (0, 1),
        (-1, -1), (1, -1), (-1, 1), (1, 1),
    )
    try:
        for _ in range(radius):
            base_known = known.copy()
            base_rgb = rgb.copy()
            try:
                for dx, dy in directions:
                    shifted_known = _shift_image(base_known, dx, dy, 0)
                    shifted_rgb = _shift_image(base_rgb, dx, dy, (0, 0, 0))
                    fill_mask = ImageChops.subtract(shifted_known, known)
                    if fill_mask.getbbox() is not None:
                        updated_rgb = Image.composite(shifted_rgb, rgb, fill_mask)
                        rgb.close()
                        rgb = updated_rgb
                        updated_known = ImageChops.lighter(known, shifted_known)
                        known.close()
                        known = updated_known
                    shifted_known.close()
                    shifted_rgb.close()
                    fill_mask.close()
            finally:
                base_known.close()
                base_rgb.close()
        result = rgb.convert("RGBA")
        result.putalpha(alpha)
        return result
    finally:
        rgba.close()
        alpha.close()
        known.close()
        rgb.close()


def _union_box(
    first: tuple[int, int, int, int] | None,
    second: tuple[int, int, int, int],
) -> tuple[int, int, int, int]:
    if first is None:
        return second
    return (
        min(first[0], second[0]),
        min(first[1], second[1]),
        max(first[2], second[2]),
        max(first[3], second[3]),
    )


def shared_source_geometry(
    actions: Sequence[tuple[ActionConfig, Sequence[Path]]],
) -> tuple[tuple[int, int], tuple[int, int, int, int]]:
    expected_size: tuple[int, int] | None = None
    content_box: tuple[int, int, int, int] | None = None
    for action, frames in actions:
        for frame in frames:
            try:
                with Image.open(frame) as image:
                    rgba = image.convert("RGBA")
                    rgba.load()
            except (OSError, ValueError) as exc:
                raise PackToolError(f"Cannot decode generated frame {frame}: {exc}") from exc
            try:
                if expected_size is None:
                    expected_size = rgba.size
                elif rgba.size != expected_size:
                    raise PackToolError(
                        "All actions must use one source canvas size so character scale and "
                        f"anchor remain stable; {action.action_id} contains {rgba.size}, "
                        f"expected {expected_size}"
                    )
                alpha = rgba.getchannel("A")
                try:
                    frame_box = alpha.getbbox()
                    if frame_box is not None:
                        content_box = _union_box(content_box, frame_box)
                finally:
                    alpha.close()
            finally:
                rgba.close()
    if expected_size is None or content_box is None:
        raise PackToolError("Character pack contains no visible pixels")
    return expected_size, content_box


def normalize_action_frames(
    action: ActionConfig,
    source_frames: Sequence[Path],
    destination: Path,
    config: BuildConfig,
    shared_source_size: tuple[int, int] | None = None,
    shared_content_box: tuple[int, int, int, int] | None = None,
) -> tuple[Path, ...]:
    target = config.target
    destination.mkdir(parents=True, exist_ok=True)
    expected_size: tuple[int, int] | None = None
    content_box: tuple[int, int, int, int] | None = None
    has_transparency = False
    for frame in source_frames:
        try:
            with Image.open(frame) as image:
                rgba = image.convert("RGBA")
                rgba.load()
        except (OSError, ValueError) as exc:
            raise PackToolError(f"Cannot decode generated frame {frame}: {exc}") from exc
        if expected_size is None:
            expected_size = rgba.size
        elif rgba.size != expected_size:
            rgba.close()
            raise PackToolError(f"Action {action.action_id} changes canvas dimensions")
        alpha = rgba.getchannel("A")
        minimum_alpha, maximum_alpha = alpha.getextrema()
        has_transparency = has_transparency or minimum_alpha < 255
        if maximum_alpha > 0 and alpha.getbbox() is not None:
            content_box = _union_box(content_box, alpha.getbbox())  # type: ignore[arg-type]
        alpha.close()
        rgba.close()

    if expected_size is None or content_box is None:
        raise PackToolError(f"Action {action.action_id} contains no visible pixels")
    if shared_source_size is not None:
        if expected_size != shared_source_size:
            raise PackToolError(
                f"Action {action.action_id} source canvas changed during normalization"
            )
        if shared_content_box is None:
            raise PackToolError("Shared character crop is missing")
        content_box = shared_content_box
    if target.require_alpha and not has_transparency:
        raise PackToolError(
            f"Action {action.action_id} decoded without transparency; provide alpha-capable "
            "video/PNGs or set target.requireAlpha=false explicitly"
        )

    crop_width = content_box[2] - content_box[0]
    crop_height = content_box[3] - content_box[1]
    available_width = target.frame_width - target.padding * 2
    available_height = target.frame_height - target.padding * 2
    scale = min(available_width / crop_width, available_height / crop_height)
    resized_width = max(1, min(available_width, round(crop_width * scale)))
    resized_height = max(1, min(available_height, round(crop_height * scale)))
    paste_x = (target.frame_width - resized_width) // 2
    paste_y = (
        target.frame_height - target.padding - resized_height
        if target.anchor == "bottom_center"
        else (target.frame_height - resized_height) // 2
    )

    outputs: list[Path] = []
    for index, frame in enumerate(source_frames):
        with Image.open(frame) as image:
            rgba = image.convert("RGBA")
            cropped = rgba.crop(content_box)
            resized = cropped.resize(
                (resized_width, resized_height), resample=Image.Resampling.LANCZOS
            )
        canvas = Image.new("RGBA", (target.frame_width, target.frame_height), (0, 0, 0, 0))
        canvas.alpha_composite(resized, (paste_x, paste_y))
        dilated = dilate_transparent_rgb(canvas, target.edge_dilate_pixels)
        output = destination / f"frame_{index:06d}.png"
        dilated.save(output, format="PNG", compress_level=9)
        outputs.append(output)
        rgba.close()
        cropped.close()
        resized.close()
        canvas.close()
        dilated.close()
    return tuple(outputs)


def process_actions(
    config: BuildConfig,
    work_root: Path,
    toolchain: Toolchain,
) -> tuple[ProcessedAction, ...]:
    temporal_actions: list[tuple[ActionConfig, tuple[Path, ...]]] = []
    for action in config.actions:
        print(f"[frames] {action.action_id}: {action.source.name}")
        extracted = temporal_frames(action, work_root / "temporal" / action.action_id, toolchain)
        temporal_actions.append((action, extracted))

    # One shared crop/scale across the pack prevents size jumps between actions and guarantees
    # pixel-identical boundary frames remain identical after normalization.
    shared_size, shared_box = shared_source_geometry(temporal_actions)
    processed: list[ProcessedAction] = []
    for action, extracted in temporal_actions:
        normalized = normalize_action_frames(
            action,
            extracted,
            work_root / "normalized" / action.action_id,
            config,
            shared_source_size=shared_size,
            shared_content_box=shared_box,
        )
        processed.append(ProcessedAction(action, normalized))
    return tuple(processed)


def choose_grid(
    frame_count: int,
    frame_width: int,
    frame_height: int,
    candidates: Iterable[int] = DEFAULT_GRID_CANDIDATES,
    max_texture_size: int = DEFAULT_MAX_TEXTURE_SIZE,
) -> TextureLayout:
    if frame_count <= 0:
        raise PackToolError("Texture frame count must be positive")
    options: list[tuple[tuple[int, int, int], int, int, int, int]] = []
    for grid in candidates:
        page_width = grid * frame_width
        page_height = grid * frame_height
        if page_width > max_texture_size or page_height > max_texture_size:
            continue
        page_count = math.ceil(frame_count / (grid * grid))
        allocated_pixels = page_width * page_height * page_count
        options.append(
            ((allocated_pixels, max(page_width, page_height), grid), grid, page_count, page_width, page_height)
        )
    if not options:
        raise PackToolError("No 4/5/6 page grid fits the configured maximum texture size")
    _, grid, page_count, page_width, page_height = min(options, key=lambda item: item[0])
    return TextureLayout(
        texture_id="",
        frame_count=frame_count,
        columns=grid,
        rows=grid,
        page_count=page_count,
        page_width=page_width,
        page_height=page_height,
        allocated_bytes=page_width * page_height * page_count,
    )


def create_texture_pages(
    texture_id: str,
    frames: Sequence[Path],
    config: BuildConfig,
    destination: Path,
) -> tuple[TextureLayout, tuple[Path, ...]]:
    target = config.target
    base = choose_grid(
        len(frames),
        target.frame_width,
        target.frame_height,
        target.grid_candidates,
        target.max_texture_size,
    )
    layout = TextureLayout(
        texture_id,
        base.frame_count,
        base.columns,
        base.rows,
        base.page_count,
        base.page_width,
        base.page_height,
        base.allocated_bytes,
    )
    texture_dir = destination / texture_id
    texture_dir.mkdir(parents=True, exist_ok=True)
    frames_per_page = layout.columns * layout.rows
    pages: list[Path] = []
    for page_index in range(layout.page_count):
        page = Image.new("RGBA", (layout.page_width, layout.page_height), (0, 0, 0, 0))
        start = page_index * frames_per_page
        end = min(start + frames_per_page, len(frames))
        for frame_index in range(start, end):
            slot = frame_index - start
            x = (slot % layout.columns) * target.frame_width
            y = (slot // layout.columns) * target.frame_height
            with Image.open(frames[frame_index]) as image:
                rgba = image.convert("RGBA")
                if rgba.size != (target.frame_width, target.frame_height):
                    raise PackToolError(f"Unexpected normalized size: {frames[frame_index]}")
                # Copy all RGBA channels verbatim. Alpha compositing onto a transparent page
                # clears RGB in alpha=0 texels, undoing the edge dilation needed to prevent
                # texture-filtering and block-compression fringes at the sprite boundary.
                page.paste(rgba, (x, y))
                rgba.close()
        page_path = texture_dir / f"page_{page_index:04d}.png"
        page.save(page_path, format="PNG", compress_level=9)
        page.close()
        pages.append(page_path)
    return layout, tuple(pages)

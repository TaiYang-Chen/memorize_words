#!/usr/bin/env python3
"""Build a schema-v2 KTX2/Basis character pack from videos or PNG frames."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
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

try:
    import yaml
    from PIL import Image, ImageChops
except ImportError as exc:  # pragma: no cover - exercised before the CLI can start
    missing = getattr(exc, "name", "a required package")
    raise SystemExit(
        f"Missing Python dependency {missing!r}. Run: "
        f"{sys.executable} -m pip install -r requirements.txt"
    ) from exc


TOOL_VERSION = "1.0.0"
TARGET_FPS = 30
MAX_FRAME_DIMENSION = 336
DEFAULT_MAX_TEXTURE_SIZE = 2016
DEFAULT_GRID_CANDIDATES = (4, 5, 6)
STANDARD_ACTIONS = ("idle", "card_open", "card_visible", "card_close")
SAFE_ID = re.compile(r"^[a-z0-9][a-z0-9_-]{0,63}$")
SAFE_EVENT = re.compile(r"^[a-z][a-z0-9_]{0,63}$")
VIDEO_EXTENSIONS = {".avi", ".m4v", ".mkv", ".mov", ".mp4", ".webm"}
KTX2_IDENTIFIER = b"\xABKTX 20\xBB\r\n\x1A\n"


class PackToolError(RuntimeError):
    """A user-actionable pack build failure."""


@dataclass(frozen=True)
class TargetConfig:
    fps: int
    frame_width: int
    frame_height: int
    padding: int
    anchor: str
    require_alpha: bool
    edge_dilate_pixels: int
    max_texture_size: int
    grid_candidates: tuple[int, ...]


@dataclass(frozen=True)
class EncoderConfig:
    preferred: str
    uastc_quality: int
    zstd_level: int
    extra_args: tuple[str, ...]


@dataclass(frozen=True)
class ActionConfig:
    action_id: str
    source: Path
    source_fps: Fraction | None
    mode: str
    next_action: str | None
    reversible: bool
    semantic: str | None
    event: str | None
    texture_id: str


@dataclass(frozen=True)
class PreviewConfig:
    action_id: str
    frame: int


@dataclass(frozen=True)
class BuildConfig:
    config_path: Path
    pack_id: str
    pack_version: int
    fallback_clip: str
    target: TargetConfig
    encoder: EncoderConfig
    preview: PreviewConfig
    actions: tuple[ActionConfig, ...]


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


@dataclass(frozen=True)
class Toolchain:
    encoder_kind: str
    encoder_path: str
    ffmpeg_path: str | None
    ffprobe_path: str | None
    validator_path: str | None = None


def _mapping(value: Any, label: str) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        raise PackToolError(f"{label} must be a mapping")
    return value


def _integer(value: Any, label: str, minimum: int | None = None) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise PackToolError(f"{label} must be an integer")
    if minimum is not None and value < minimum:
        raise PackToolError(f"{label} must be >= {minimum}")
    return value


def _boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise PackToolError(f"{label} must be true or false")
    return value


def _safe_id(value: Any, label: str) -> str:
    if not isinstance(value, str) or not SAFE_ID.fullmatch(value):
        raise PackToolError(
            f"{label} must match {SAFE_ID.pattern} (lowercase ASCII, max 64 characters)"
        )
    return value


def _fraction(value: Any, label: str) -> Fraction:
    if isinstance(value, bool):
        raise PackToolError(f"{label} must be a positive frame rate")
    try:
        result = Fraction(str(value))
    except (ValueError, ZeroDivisionError) as exc:
        raise PackToolError(f"{label} must be a positive frame rate") from exc
    if result <= 0:
        raise PackToolError(f"{label} must be a positive frame rate")
    return result


def _resolve_source(config_dir: Path, value: Any, label: str) -> Path:
    if not isinstance(value, str) or not value.strip():
        raise PackToolError(f"{label} must be a non-empty relative path")
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts:
        raise PackToolError(f"{label} must stay inside the directory containing pack.yaml")
    resolved = (config_dir / relative).resolve()
    try:
        resolved.relative_to(config_dir.resolve())
    except ValueError as exc:
        raise PackToolError(f"{label} resolves outside the pack source directory") from exc
    if not resolved.exists():
        raise PackToolError(f"{label} does not exist: {resolved}")
    return resolved


def load_config(config_path: Path) -> BuildConfig:
    config_path = config_path.resolve()
    if not config_path.is_file():
        raise PackToolError(f"Configuration file does not exist: {config_path}")
    try:
        root_value = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        raise PackToolError(f"Cannot read YAML configuration: {exc}") from exc
    root = _mapping(root_value, "pack.yaml")

    allowed_root = {
        "packId",
        "packVersion",
        "fallbackClip",
        "target",
        "encoder",
        "preview",
        "actions",
    }
    unknown_root = sorted(set(root) - allowed_root)
    if unknown_root:
        raise PackToolError(f"Unknown top-level keys: {', '.join(unknown_root)}")

    pack_id = _safe_id(root.get("packId"), "packId")
    pack_version = _integer(root.get("packVersion"), "packVersion", 1)
    fallback_clip = _safe_id(root.get("fallbackClip", "idle"), "fallbackClip")

    target_raw = _mapping(root.get("target", {}), "target")
    allowed_target = {
        "fps",
        "frameWidth",
        "frameHeight",
        "padding",
        "anchor",
        "requireAlpha",
        "edgeDilatePixels",
        "maxTextureSize",
        "gridCandidates",
    }
    unknown_target = sorted(set(target_raw) - allowed_target)
    if unknown_target:
        raise PackToolError(f"Unknown target keys: {', '.join(unknown_target)}")
    fps = _integer(target_raw.get("fps", TARGET_FPS), "target.fps", 1)
    if fps != TARGET_FPS:
        raise PackToolError("target.fps must be 30; runtime packs use a fixed 30 FPS timeline")
    frame_width = _integer(target_raw.get("frameWidth", 336), "target.frameWidth", 1)
    frame_height = _integer(target_raw.get("frameHeight", 336), "target.frameHeight", 1)
    if frame_width > MAX_FRAME_DIMENSION or frame_height > MAX_FRAME_DIMENSION:
        raise PackToolError(
            f"Target frames may not exceed {MAX_FRAME_DIMENSION}x{MAX_FRAME_DIMENSION}"
        )
    padding = _integer(target_raw.get("padding", 8), "target.padding", 0)
    if padding * 2 >= min(frame_width, frame_height):
        raise PackToolError("target.padding leaves no drawable area")
    anchor = target_raw.get("anchor", "bottom_center")
    if anchor not in {"center", "bottom_center"}:
        raise PackToolError("target.anchor must be center or bottom_center")
    require_alpha = _boolean(target_raw.get("requireAlpha", True), "target.requireAlpha")
    edge_dilate_pixels = _integer(
        target_raw.get("edgeDilatePixels", 4), "target.edgeDilatePixels", 0
    )
    if edge_dilate_pixels > 16:
        raise PackToolError("target.edgeDilatePixels may not exceed 16")
    max_texture_size = _integer(
        target_raw.get("maxTextureSize", DEFAULT_MAX_TEXTURE_SIZE),
        "target.maxTextureSize",
        1,
    )
    if max_texture_size > DEFAULT_MAX_TEXTURE_SIZE:
        raise PackToolError(
            f"target.maxTextureSize may not exceed {DEFAULT_MAX_TEXTURE_SIZE} for GLES2 support"
        )
    grid_raw = target_raw.get("gridCandidates", list(DEFAULT_GRID_CANDIDATES))
    if not isinstance(grid_raw, list) or not grid_raw:
        raise PackToolError("target.gridCandidates must be a non-empty list")
    grid_candidates = tuple(
        _integer(value, f"target.gridCandidates[{index}]", 1)
        for index, value in enumerate(grid_raw)
    )
    if len(set(grid_candidates)) != len(grid_candidates):
        raise PackToolError("target.gridCandidates must not contain duplicates")
    if any(value not in DEFAULT_GRID_CANDIDATES for value in grid_candidates):
        raise PackToolError("target.gridCandidates may contain only 4, 5, and 6")
    if not any(
        grid * frame_width <= max_texture_size and grid * frame_height <= max_texture_size
        for grid in grid_candidates
    ):
        raise PackToolError("No grid candidate fits target.maxTextureSize")

    encoder_raw = _mapping(root.get("encoder", {}), "encoder")
    allowed_encoder = {"preferred", "uastcQuality", "zstdLevel", "extraArgs"}
    unknown_encoder = sorted(set(encoder_raw) - allowed_encoder)
    if unknown_encoder:
        raise PackToolError(f"Unknown encoder keys: {', '.join(unknown_encoder)}")
    preferred = encoder_raw.get("preferred", "auto")
    if preferred not in {"auto", "toktx", "basisu"}:
        raise PackToolError("encoder.preferred must be auto, toktx, or basisu")
    uastc_quality = _integer(
        encoder_raw.get("uastcQuality", 2), "encoder.uastcQuality", 0
    )
    if uastc_quality > 4:
        raise PackToolError("encoder.uastcQuality must be between 0 and 4")
    zstd_level = _integer(encoder_raw.get("zstdLevel", 18), "encoder.zstdLevel", 1)
    if zstd_level > 22:
        raise PackToolError("encoder.zstdLevel must be between 1 and 22")
    extra_raw = encoder_raw.get("extraArgs", [])
    if not isinstance(extra_raw, list) or not all(isinstance(item, str) for item in extra_raw):
        raise PackToolError("encoder.extraArgs must be a list of strings")
    extra_args = tuple(extra_raw)

    actions_raw = _mapping(root.get("actions"), "actions")
    if not actions_raw:
        raise PackToolError("actions may not be empty")
    missing_standard = [action for action in STANDARD_ACTIONS if action not in actions_raw]
    if missing_standard:
        raise PackToolError(
            "Missing required standard actions: " + ", ".join(missing_standard)
        )

    action_order = list(STANDARD_ACTIONS) + [
        str(action_id) for action_id in actions_raw if action_id not in STANDARD_ACTIONS
    ]
    actions: list[ActionConfig] = []
    semantics: set[str] = set()
    events: set[str] = set()
    config_dir = config_path.parent
    for action_id_value in action_order:
        action_id = _safe_id(action_id_value, "actions key")
        action_raw = _mapping(actions_raw[action_id_value], f"actions.{action_id}")
        allowed_action = {
            "source",
            "sourceFps",
            "mode",
            "next",
            "reversible",
            "semantic",
            "event",
            "texture",
        }
        unknown_action = sorted(set(action_raw) - allowed_action)
        if unknown_action:
            raise PackToolError(
                f"Unknown keys in actions.{action_id}: {', '.join(unknown_action)}"
            )
        source = _resolve_source(
            config_dir, action_raw.get("source"), f"actions.{action_id}.source"
        )
        source_fps = None
        if "sourceFps" in action_raw:
            source_fps = _fraction(
                action_raw["sourceFps"], f"actions.{action_id}.sourceFps"
            )
        mode = action_raw.get("mode")
        if mode not in {"once", "loop", "hold_last"}:
            raise PackToolError(
                f"actions.{action_id}.mode must be once, loop, or hold_last"
            )
        next_action = action_raw.get("next")
        if next_action is not None:
            next_action = _safe_id(next_action, f"actions.{action_id}.next")
            if mode == "loop":
                raise PackToolError(f"Loop action {action_id} may not declare next")
        reversible = _boolean(
            action_raw.get("reversible", False), f"actions.{action_id}.reversible"
        )
        semantic = action_raw.get("semantic")
        if semantic is None and action_id in STANDARD_ACTIONS:
            semantic = action_id
        if semantic is not None:
            semantic = _safe_id(semantic, f"actions.{action_id}.semantic")
            if semantic in semantics:
                raise PackToolError(f"Duplicate semantic binding: {semantic}")
            semantics.add(semantic)
        event = action_raw.get("event")
        if event is not None:
            if not isinstance(event, str) or not SAFE_EVENT.fullmatch(event):
                raise PackToolError(
                    f"actions.{action_id}.event must match {SAFE_EVENT.pattern}"
                )
            if event in events:
                raise PackToolError(f"Duplicate event binding: {event}")
            events.add(event)
        texture_id = _safe_id(
            action_raw.get("texture", "standard"), f"actions.{action_id}.texture"
        )
        actions.append(
            ActionConfig(
                action_id=action_id,
                source=source,
                source_fps=source_fps,
                mode=mode,
                next_action=next_action,
                reversible=reversible,
                semantic=semantic,
                event=event,
                texture_id=texture_id,
            )
        )

    action_ids = {action.action_id for action in actions}
    if fallback_clip not in action_ids:
        raise PackToolError(f"fallbackClip references an unknown action: {fallback_clip}")
    for action in actions:
        if action.next_action is not None and action.next_action not in action_ids:
            raise PackToolError(
                f"actions.{action.action_id}.next references unknown action {action.next_action}"
            )
    missing_semantics = sorted(set(STANDARD_ACTIONS) - semantics)
    if missing_semantics:
        raise PackToolError(
            "Required semantic bindings are missing: " + ", ".join(missing_semantics)
        )
    actions_by_id = {action.action_id: action for action in actions}
    required_modes = {
        "idle": "hold_last",
        "card_open": "once",
        "card_visible": "loop",
        "card_close": "once",
    }
    required_next = {
        "card_open": "card_visible",
        "card_close": "idle",
    }
    for action_id in STANDARD_ACTIONS:
        action = actions_by_id[action_id]
        if action.semantic != action_id:
            raise PackToolError(f"actions.{action_id}.semantic must be {action_id}")
        if action.mode != required_modes[action_id]:
            raise PackToolError(
                f"actions.{action_id}.mode must be {required_modes[action_id]}"
            )
        if action_id in {"card_open", "card_close"} and not action.reversible:
            raise PackToolError(f"actions.{action_id}.reversible must be true")
        expected_next = required_next.get(action_id)
        if expected_next is not None and action.next_action != expected_next:
            raise PackToolError(f"actions.{action_id}.next must be {expected_next}")
    for action in actions:
        is_optional_semantic = (
            action.semantic is not None and action.semantic not in STANDARD_ACTIONS
        )
        if (action.event is not None or is_optional_semantic) and action.mode == "loop":
            raise PackToolError(
                f"Optional action {action.action_id} must be finite until a stop event exists"
            )

    preview_raw = _mapping(root.get("preview", {}), "preview")
    unknown_preview = sorted(set(preview_raw) - {"action", "frame"})
    if unknown_preview:
        raise PackToolError(f"Unknown preview keys: {', '.join(unknown_preview)}")
    preview_action = _safe_id(
        preview_raw.get("action", "card_visible"), "preview.action"
    )
    if preview_action not in action_ids:
        raise PackToolError(f"preview.action references unknown action {preview_action}")
    preview_frame = _integer(preview_raw.get("frame", 0), "preview.frame", 0)

    return BuildConfig(
        config_path=config_path,
        pack_id=pack_id,
        pack_version=pack_version,
        fallback_clip=fallback_clip,
        target=TargetConfig(
            fps=fps,
            frame_width=frame_width,
            frame_height=frame_height,
            padding=padding,
            anchor=anchor,
            require_alpha=require_alpha,
            edge_dilate_pixels=edge_dilate_pixels,
            max_texture_size=max_texture_size,
            grid_candidates=grid_candidates,
        ),
        encoder=EncoderConfig(
            preferred=preferred,
            uastc_quality=uastc_quality,
            zstd_level=zstd_level,
            extra_args=extra_args,
        ),
        preview=PreviewConfig(preview_action, preview_frame),
        actions=tuple(actions),
    )


def natural_sort_key(path: Path) -> tuple[Any, ...]:
    return tuple(
        int(part) if part.isdigit() else part.lower()
        for part in re.split(r"(\d+)", path.name)
    )


def png_sources(source: Path) -> tuple[Path, ...]:
    if source.is_file():
        if source.suffix.lower() != ".png":
            raise PackToolError(f"Single-frame image source must be PNG: {source}")
        return (source,)
    if not source.is_dir():
        raise PackToolError(f"Action source is neither a file nor a directory: {source}")
    frames = tuple(
        sorted(
            (path for path in source.iterdir() if path.is_file() and path.suffix.lower() == ".png"),
            key=natural_sort_key,
        )
    )
    non_png = [path.name for path in source.iterdir() if path.is_file() and path.suffix.lower() != ".png"]
    if non_png:
        raise PackToolError(
            f"PNG sequence directory {source} contains non-PNG files: "
            + ", ".join(sorted(non_png)[:5])
        )
    if not frames:
        raise PackToolError(f"PNG sequence directory is empty: {source}")
    return frames


def is_video_source(path: Path) -> bool:
    return path.is_file() and path.suffix.lower() in VIDEO_EXTENSIONS


def _find_executable(explicit: str | None, names: Sequence[str], label: str) -> str:
    if explicit:
        candidate = Path(explicit).expanduser()
        if candidate.is_file():
            return str(candidate.resolve())
        located = shutil.which(explicit)
        if located:
            return located
        raise PackToolError(f"Configured {label} executable was not found: {explicit}")
    for name in names:
        located = shutil.which(name)
        if located:
            return located
    raise PackToolError(f"Missing {label}. Install it and add it to PATH.")


def resolve_toolchain(
    config: BuildConfig,
    encoder_override: str | None,
    encoder_path: str | None,
    ffmpeg_path: str | None,
    ffprobe_path: str | None,
    validator_path: str | None = None,
) -> Toolchain:
    requested = encoder_override or config.encoder.preferred
    if requested not in {"auto", "toktx", "basisu"}:
        raise PackToolError("--encoder must be auto, toktx, or basisu")
    if encoder_path:
        resolved_encoder = _find_executable(encoder_path, (), "KTX2 encoder")
        encoder_kind = requested if requested != "auto" else _encoder_kind_from_name(resolved_encoder)
    elif requested == "toktx":
        resolved_encoder = _find_executable(None, ("toktx", "toktx.exe"), "toktx")
        encoder_kind = "toktx"
    elif requested == "basisu":
        resolved_encoder = _find_executable(None, ("basisu", "basisu.exe"), "basisu")
        encoder_kind = "basisu"
    else:
        toktx = shutil.which("toktx") or shutil.which("toktx.exe")
        basisu = shutil.which("basisu") or shutil.which("basisu.exe")
        if toktx:
            resolved_encoder = toktx
            encoder_kind = "toktx"
        elif basisu:
            resolved_encoder = basisu
            encoder_kind = "basisu"
        else:
            raise PackToolError(
                "Missing KTX2 encoder. Install KTX-Software (toktx preferred) or Basis Universal (basisu)."
            )

    resolved_validator = _find_executable(
        validator_path,
        ("basis_ktx2_validator", "basis_ktx2_validator.exe"),
        "Basis KTX2 host validator",
    )

    has_video = any(is_video_source(action.source) for action in config.actions)
    resolved_ffmpeg = None
    resolved_ffprobe = None
    if has_video:
        resolved_ffmpeg = _find_executable(
            ffmpeg_path, ("ffmpeg", "ffmpeg.exe"), "FFmpeg"
        )
        resolved_ffprobe = _find_executable(
            ffprobe_path, ("ffprobe", "ffprobe.exe"), "ffprobe"
        )
    return Toolchain(
        encoder_kind=encoder_kind,
        encoder_path=resolved_encoder,
        ffmpeg_path=resolved_ffmpeg,
        ffprobe_path=resolved_ffprobe,
        validator_path=resolved_validator,
    )


def _encoder_kind_from_name(path: str) -> str:
    name = Path(path).stem.lower()
    if "toktx" in name:
        return "toktx"
    if "basisu" in name:
        return "basisu"
    raise PackToolError(
        "Cannot infer encoder kind from --encoder-path; also pass --encoder toktx or --encoder basisu"
    )


def run_command(command: Sequence[str], cwd: Path | None = None) -> str:
    try:
        completed = subprocess.run(
            list(command),
            cwd=str(cwd) if cwd else None,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as exc:
        raise PackToolError(f"Cannot start {command[0]}: {exc}") from exc
    if completed.returncode != 0:
        rendered = subprocess.list2cmdline(list(command))
        raise PackToolError(
            f"External command failed with exit code {completed.returncode}:\n"
            f"{rendered}\n{completed.stdout.strip()}"
        )
    return completed.stdout.strip()


def probe_video(ffprobe: str, source: Path) -> Mapping[str, Any]:
    output = run_command(
        (
            ffprobe,
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=width,height,avg_frame_rate,r_frame_rate,pix_fmt,nb_frames,duration",
            "-of",
            "json",
            str(source),
        )
    )
    try:
        payload = json.loads(output)
        streams = payload["streams"]
        stream = streams[0]
    except (KeyError, IndexError, TypeError, json.JSONDecodeError) as exc:
        raise PackToolError(f"ffprobe returned no readable video stream for {source}") from exc
    if not isinstance(stream, Mapping):
        raise PackToolError(f"ffprobe returned an invalid stream for {source}")
    return stream


def video_frame_rate(stream: Mapping[str, Any], source: Path) -> Fraction:
    raw = stream.get("avg_frame_rate") or stream.get("r_frame_rate")
    try:
        fps = Fraction(str(raw))
    except (ValueError, ZeroDivisionError) as exc:
        raise PackToolError(f"Cannot determine source frame rate for {source}") from exc
    if fps <= 0:
        raise PackToolError(f"Cannot determine source frame rate for {source}")
    return fps


def validate_inputs(config: BuildConfig, toolchain: Toolchain) -> list[str]:
    warnings: list[str] = []
    for action in config.actions:
        if is_video_source(action.source):
            assert toolchain.ffprobe_path is not None
            stream = probe_video(toolchain.ffprobe_path, action.source)
            width = int(stream.get("width") or 0)
            height = int(stream.get("height") or 0)
            if width <= 0 or height <= 0:
                raise PackToolError(f"Video has invalid dimensions: {action.source}")
            fps = video_frame_rate(stream, action.source)
            pixel_format = str(stream.get("pix_fmt") or "unknown").lower()
            alpha_markers = ("rgba", "bgra", "argb", "abgr", "yuva", "gbrap", "ya")
            if config.target.require_alpha and not any(marker in pixel_format for marker in alpha_markers):
                warnings.append(
                    f"{action.action_id}: ffprobe reports pixel format {pixel_format!r}; "
                    "the decoded frames will be rejected if they contain no transparency"
                )
            if fps != TARGET_FPS:
                warnings.append(
                    f"{action.action_id}: {float(fps):.3f} FPS will be alpha-safe "
                    "interpolated to 30 FPS"
                )
        else:
            frames = png_sources(action.source)
            if len(frames) > 1 and action.source_fps is None:
                raise PackToolError(
                    f"actions.{action.action_id}.sourceFps is required for a PNG sequence"
                )
            expected_size: tuple[int, int] | None = None
            has_transparency = False
            has_content = False
            for frame in frames:
                try:
                    with Image.open(frame) as image:
                        image.load()
                        rgba = image.convert("RGBA")
                except (OSError, ValueError) as exc:
                    raise PackToolError(f"Cannot decode PNG frame {frame}: {exc}") from exc
                if expected_size is None:
                    expected_size = rgba.size
                elif rgba.size != expected_size:
                    raise PackToolError(
                        f"PNG sequence {action.source} mixes dimensions: "
                        f"expected {expected_size}, found {rgba.size} in {frame.name}"
                    )
                minimum_alpha, maximum_alpha = rgba.getchannel("A").getextrema()
                has_transparency = has_transparency or minimum_alpha < 255
                has_content = has_content or maximum_alpha > 0
            if not has_content:
                raise PackToolError(f"Action {action.action_id} contains only transparent frames")
            if config.target.require_alpha and not has_transparency:
                raise PackToolError(
                    f"Action {action.action_id} contains no transparent pixels; "
                    "provide RGBA PNGs or set target.requireAlpha=false explicitly"
                )
            if action.source_fps is not None and action.source_fps != TARGET_FPS:
                warnings.append(
                    f"{action.action_id}: {float(action.source_fps):.3f} FPS PNGs will be alpha-aware blended to 30 FPS"
                )
    return warnings

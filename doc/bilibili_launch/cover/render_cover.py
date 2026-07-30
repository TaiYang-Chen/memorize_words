from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


WIDTH = 1920
HEIGHT = 1080
INK = "#141716"
PAPER = "#f4f6f1"
WHITE = "#ffffff"
BLUE = "#315ee8"
CORAL = "#ff684f"
YELLOW = "#ffd44f"
MUTED = "#626b66"
APP_NAME = "炫羲单词"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Render the Bilibili cover.")
    parser.add_argument("--brand", default=APP_NAME)
    parser.add_argument("--screenshot", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--final", action="store_true")
    return parser.parse_args()


def font_path(bold: bool = False) -> Path:
    candidates = (
        [Path("C:/Windows/Fonts/msyhbd.ttc"), Path("C:/Windows/Fonts/simhei.ttf")]
        if bold
        else [Path("C:/Windows/Fonts/msyh.ttc"), Path("C:/Windows/Fonts/simsun.ttc")]
    )
    for candidate in candidates:
        if candidate.exists():
            return candidate
    raise FileNotFoundError("No supported Chinese font found in C:/Windows/Fonts")


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(font_path(bold)), size=size)


def fit_font(draw: ImageDraw.ImageDraw, text: str, max_width: int, start_size: int) -> ImageFont.FreeTypeFont:
    size = start_size
    while size >= 20:
        font = load_font(size, bold=True)
        bounds = draw.textbbox((0, 0), text, font=font)
        if bounds[2] - bounds[0] <= max_width:
            return font
        size -= 2
    return load_font(20, bold=True)


def dashed_rectangle(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    fill: str,
    outline: str,
    width: int = 5,
    dash: int = 24,
    gap: int = 16,
) -> None:
    left, top, right, bottom = box
    draw.rectangle(box, fill=fill)
    for x in range(left, right, dash + gap):
        draw.line((x, top, min(x + dash, right), top), fill=outline, width=width)
        draw.line((x, bottom, min(x + dash, right), bottom), fill=outline, width=width)
    for y in range(top, bottom, dash + gap):
        draw.line((left, y, left, min(y + dash, bottom)), fill=outline, width=width)
        draw.line((right, y, right, min(y + dash, bottom)), fill=outline, width=width)


def paste_cover_crop(canvas: Image.Image, asset: Image.Image, box: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = box
    target_width = right - left
    target_height = bottom - top
    image = asset.convert("RGB")
    scale = max(target_width / image.width, target_height / image.height)
    resized = image.resize(
        (round(image.width * scale), round(image.height * scale)),
        Image.Resampling.LANCZOS,
    )
    crop_left = (resized.width - target_width) // 2
    crop_top = (resized.height - target_height) // 2
    cropped = resized.crop((crop_left, crop_top, crop_left + target_width, crop_top + target_height)).convert("RGBA")
    mask = Image.new("L", (target_width, target_height), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, target_width, target_height), radius=35, fill=255)
    layer = Image.new("RGBA", cropped.size)
    layer.paste(cropped, (0, 0), mask)
    canvas.alpha_composite(layer, (left, top))


def centered_text(
    draw: ImageDraw.ImageDraw,
    center_x: int,
    y: int,
    text: str,
    font: ImageFont.FreeTypeFont,
    fill: str,
) -> None:
    bounds = draw.textbbox((0, 0), text, font=font)
    draw.text((center_x - (bounds[2] - bounds[0]) // 2, y), text, font=font, fill=fill)


def render(brand: str, screenshot: Path, output: Path, final: bool) -> None:
    if final and brand != APP_NAME:
        raise ValueError(f"--final requires the fixed brand: {APP_NAME}")
    if final and not screenshot.exists():
        raise FileNotFoundError("--final requires cover/cross-app-screenshot.png")

    canvas = Image.new("RGBA", (WIDTH, HEIGHT), PAPER)
    draw = ImageDraw.Draw(canvas)

    for x in range(0, WIDTH, 64):
        draw.line((x, 0, x, HEIGHT), fill="#e7e9e4", width=1)
    for y in range(0, HEIGHT, 64):
        draw.line((0, y, WIDTH, y), fill="#e7e9e4", width=1)
    draw.rectangle((0, 0, WIDTH, 18), fill=INK)
    draw.rectangle((0, 670, 34, HEIGHT), fill=CORAL)

    draw.rectangle((88, 106, 406, 169), fill=YELLOW, outline=INK, width=3)
    draw.text((112, 120), "词源：已学词", font=load_font(28, bold=True), fill=INK)

    headline_font = fit_font(draw, "跟着我刷手机", 840, 128)
    draw.text((90, 205), "背过的词", font=headline_font, fill=INK)
    headline_bounds = draw.textbbox((0, 0), "跟着我刷手机", font=headline_font)
    headline_width = headline_bounds[2] - headline_bounds[0]
    draw.rectangle((84, 502, 114 + headline_width, 530), fill=YELLOW)
    draw.text((90, 350), "跟着我刷手机", font=headline_font, fill=BLUE)
    draw.text((96, 630), brand, font=fit_font(draw, brand, 760, 43), fill=INK)
    draw.text((96, 706), "桌宠在其他 App 上展示已学词", font=load_font(29, bold=True), fill=INK)

    draw.rectangle((1138, 166, 1898, 1002), fill=INK)
    draw.rectangle((1118, 146, 1878, 982), fill=BLUE, outline=INK, width=4)
    draw.rounded_rectangle((1102, 60, 1642, 1010), radius=54, fill="#20232180")
    draw.rounded_rectangle((1082, 52, 1622, 1002), radius=54, fill=INK, outline=INK, width=6)
    screen_box = (1100, 70, 1604, 984)
    draw.rounded_rectangle(screen_box, radius=35, fill="#e7ebe6")

    if screenshot.exists():
        paste_cover_crop(canvas, Image.open(screenshot), screen_box)
    else:
        dashed_rectangle(draw, screen_box, fill="#e7ebe6", outline="#777d79")
        centered_text(draw, 1352, 420, "替换实机截图", load_font(38, bold=True), INK)
        lines = ["cross-app-screenshot.png", "同时看到其他 App、桌宠", "和真实已学词卡"]
        for index, line in enumerate(lines):
            centered_text(draw, 1352, 500 + index * 42, line, load_font(23), MUTED)
    draw.rounded_rectangle((1281, 67, 1423, 98), radius=16, fill=INK)

    if not final:
        draw.rectangle((0, HEIGHT - 74, WIDTH, HEIGHT), fill=CORAL, outline=INK, width=4)
        draft_text = "DRAFT · 替换真实实机截图后再导出"
        centered_text(draw, WIDTH // 2, HEIGHT - 58, draft_text, load_font(30, bold=True), WHITE)

    output.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(output, format="PNG", optimize=True)


def main() -> None:
    args = parse_args()
    script_dir = Path(__file__).resolve().parent
    screenshot = args.screenshot or script_dir / "cross-app-screenshot.png"
    output = args.output or script_dir / ("cover-final.png" if args.final else "cover-draft.png")
    render(args.brand, screenshot, output, args.final)
    print(output)


if __name__ == "__main__":
    main()

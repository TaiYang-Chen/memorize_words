from pathlib import Path
from shutil import copy2

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[3]
OUTPUT_DIR = ROOT / "output" / "imagegen" / "word-popup-concepts"
NATIVE_DIR = OUTPUT_DIR / "native"
PROMPT_DIR = ROOT / "tmp" / "imagegen" / "word-popup-concepts"
TARGET_SIZE = (1088, 2400)

CONCEPTS = [
    ("01 极简紧凑", "popup-concept-01-minimal.png", "01-minimal.txt"),
    ("02 双栏发音", "popup-concept-02-dual-column.png", "02-dual-column.txt"),
    ("03 标题操作栏", "popup-concept-03-title-actions.png", "03-title-actions.txt"),
    ("04 词典编辑风", "popup-concept-04-editorial-dictionary.png", "04-editorial-dictionary.txt"),
    ("05 居中聚焦", "popup-concept-05-centered-focus.png", "05-centered-focus.txt"),
    ("06 深色标题带", "popup-concept-06-dark-header.png", "06-dark-header.txt"),
]


def load_font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    candidates = [
        Path("C:/Windows/Fonts/msyhbd.ttc"),
        Path("C:/Windows/Fonts/msyh.ttc"),
        Path("C:/Windows/Fonts/segoeuib.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def normalize_concept_sizes() -> None:
    NATIVE_DIR.mkdir(exist_ok=True)
    for _, image_name, _ in CONCEPTS:
        image_path = OUTPUT_DIR / image_name
        with Image.open(image_path) as source:
            if source.size == TARGET_SIZE:
                continue
            native_path = NATIVE_DIR / image_name
            if not native_path.exists():
                copy2(image_path, native_path)
            resized = source.convert("RGB").resize(TARGET_SIZE, Image.Resampling.LANCZOS)
        resized.save(image_path, optimize=True)


def make_overview() -> None:
    columns = 3
    rows = 2
    thumb_width = 400
    thumb_height = round(TARGET_SIZE[1] * thumb_width / TARGET_SIZE[0])
    label_height = 58
    gutter = 20
    canvas_width = columns * thumb_width + (columns + 1) * gutter
    canvas_height = rows * (label_height + thumb_height) + (rows + 1) * gutter

    canvas = Image.new("RGB", (canvas_width, canvas_height), "#F2F4F7")
    draw = ImageDraw.Draw(canvas)
    font = load_font(25)

    for index, (label, image_name, _) in enumerate(CONCEPTS):
        column = index % columns
        row = index // columns
        x = gutter + column * (thumb_width + gutter)
        y = gutter + row * (label_height + thumb_height + gutter)

        draw.rounded_rectangle(
            (x, y, x + thumb_width, y + label_height),
            radius=6,
            fill="#0A1730",
        )
        text_box = draw.textbbox((0, 0), label, font=font)
        text_height = text_box[3] - text_box[1]
        draw.text(
            (x + 18, y + (label_height - text_height) // 2 - text_box[1]),
            label,
            font=font,
            fill="white",
        )

        with Image.open(OUTPUT_DIR / image_name) as source:
            thumbnail = source.convert("RGB").resize(
                (thumb_width, thumb_height), Image.Resampling.LANCZOS
            )
        image_y = y + label_height
        canvas.paste(thumbnail, (x, image_y))
        draw.rectangle(
            (x, image_y, x + thumb_width - 1, image_y + thumb_height - 1),
            outline="#CBD1D8",
            width=1,
        )

    canvas.save(OUTPUT_DIR / "popup-concepts-overview.png", optimize=True)


def write_prompt_index() -> None:
    sections = [
        "# Word Popup Concepts - gpt-image-2 Prompts",
        "",
        "Each image was created as a separate `edit` request with `quality=medium` and `size=1088x2400`.",
        "The custom endpoint returned smaller native rasters, retained in `native/`; the canonical files were normalized locally to 1088x2400 with Lanczos resampling.",
    ]
    for label, _, prompt_name in CONCEPTS:
        prompt = (PROMPT_DIR / prompt_name).read_text(encoding="utf-8").strip()
        sections.extend(["", f"## {label}", "", "```text", prompt, "```"])
    (OUTPUT_DIR / "prompts.md").write_text("\n".join(sections) + "\n", encoding="utf-8")


if __name__ == "__main__":
    normalize_concept_sizes()
    make_overview()
    write_prompt_index()

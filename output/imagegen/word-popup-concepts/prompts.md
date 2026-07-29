# Word Popup Concepts - gpt-image-2 Prompts

Each image was created as a separate `edit` request with `quality=medium` and `size=1088x2400`.
The custom endpoint returned smaller native rasters, retained in `native/`; the canonical files were normalized locally to 1088x2400 with Lanczos resampling.

## 01 极简紧凑

```text
Use case: ui-mockup
Asset type: shippable Android vocabulary-learning popup concept, full-screen screenshot
Input image: Image 1 is the edit target.
Primary request: Redesign ONLY the existing white word-detail popup card in the lower-middle area into concept 01, a compact minimalist dictionary popup. Keep the popup attached to exactly the same anchor point and keep roughly the same outer footprint.
Popup design: pure white card, 12 dp corner radius, very thin cool-light-gray 1 dp border, absolutely no shadow, no elevation, no glow. Use tight but comfortable internal spacing and a clear left-aligned hierarchy. Put the bold word title at top. Below it, create two compact pronunciation rows. Each row has the locale label, phonetic text, and at the far right a conventional dark navy loudspeaker icon with sound waves. The speaker icons must have completely transparent backgrounds: no circle, square, pill, chip, fill, border, or button surface. Add the definition below. Make the bottom action a lightweight centered dark-navy text command separated by one subtle hairline instead of a filled button.
Text, render verbatim with no substitutions and no extra text:
"presence"
"美 /ˈprezəns/"
"英 /ˈprezəns/"
"n. 出席, 面前, 存在, 仪态, 风度"
"查看完整释义"
Typography: practical Android sans-serif; title bold; body compact and highly legible; exact Chinese and Latin characters.
Color palette: white, deep navy, neutral gray, and a small restrained blue accent only.
Critical invariants: Change only the popup card and its contents. Preserve the complete page outside the card, status bar, top toolbar, underlying vocabulary content, gray 12 percent dim overlay, visible anchor marker, and bottom next-word button as closely as possible. The area outside the card must stay uniformly dimmed. No popup shadow anywhere.
Avoid: gradients, purple, decorative illustration, glass effects, blur, card shadow, outer glow, background behind speaker icons, extra controls, extra labels, spelling errors, distorted text, device frame, watermark.
```

## 02 双栏发音

```text
Use case: ui-mockup
Asset type: shippable Android vocabulary-learning popup concept, full-screen screenshot
Input image: Image 1 is the edit target.
Primary request: Redesign ONLY the existing white word-detail popup card in the lower-middle area into concept 02, a balanced dual-column pronunciation layout. Keep the popup attached to exactly the same anchor point and keep roughly the same outer footprint.
Popup design: pure white card, 14 dp corner radius, fine cool-gray 1 dp border, absolutely no shadow, no elevation, no glow. Place the bold word title at the top left. Under it, split one pronunciation area into two equal columns using only a subtle vertical hairline, not nested cards. Left column is US pronunciation and right column is UK pronunciation. Each column shows the locale label above the phonetic text, with one conventional dark navy loudspeaker icon beside the phonetic text. Both speaker icons must sit directly on white with fully transparent backgrounds: no circle, square, pill, chip, fill, border, or button surface. Put the definition across the full card width below a horizontal divider. Use a full-width deep-navy bottom action with moderate 10 dp corner radius.
Text, render verbatim with no substitutions and no extra text:
"presence"
"美"
"/ˈprezəns/"
"英"
"/ˈprezəns/"
"n. 出席, 面前, 存在, 仪态, 风度"
"查看完整释义"
Typography: practical Android sans-serif; strong title; compact labels; highly legible exact Chinese and Latin characters.
Color palette: white, deep navy, neutral gray, and restrained blue accents only.
Critical invariants: Change only the popup card and its contents. Preserve the complete page outside the card, status bar, top toolbar, underlying vocabulary content, gray 12 percent dim overlay, visible anchor marker, and bottom next-word button as closely as possible. The area outside the card must stay uniformly dimmed. No popup shadow anywhere.
Avoid: gradients, purple, decorative illustration, nested cards, glass effects, blur, card shadow, outer glow, background behind speaker icons, extra controls, spelling errors, distorted text, device frame, watermark.
```

## 03 标题操作栏

```text
Use case: ui-mockup
Asset type: shippable Android vocabulary-learning popup concept, full-screen screenshot
Input image: Image 1 is the edit target.
Primary request: Redesign ONLY the existing white word-detail popup card in the lower-middle area into concept 03, a title action-bar layout. Keep the popup attached to exactly the same anchor point and keep roughly the same outer footprint.
Popup design: pure white card, 12 dp corner radius, thin cool-gray 1 dp border, absolutely no shadow, no elevation, no glow. Build a compact top row: bold "presence" on the left; on the right, two small locale labels paired with conventional dark navy loudspeaker icons. The icons are the primary pronunciation actions and must have fully transparent backgrounds: no circle, square, pill, chip, fill, border, or button surface. Under a subtle hairline, show the two phonetic strings as clean left-aligned rows. Put the definition in its own calm text block. Make the bottom action a compact deep-navy rounded rectangle aligned inside the content area, leaving balanced white margins.
Text, render verbatim with no substitutions and no extra text:
"presence"
"美"
"英"
"/ˈprezəns/"
"/ˈprezəns/"
"n. 出席, 面前, 存在, 仪态, 风度"
"查看完整释义"
Typography: practical Android sans-serif; title bold; clear compact hierarchy; exact Chinese and Latin characters.
Color palette: white, deep navy, neutral gray, and a small restrained blue accent only.
Critical invariants: Change only the popup card and its contents. Preserve the complete page outside the card, status bar, top toolbar, underlying vocabulary content, gray 12 percent dim overlay, visible anchor marker, and bottom next-word button as closely as possible. The area outside the card must stay uniformly dimmed. No popup shadow anywhere.
Avoid: gradients, purple, decorative illustration, glass effects, blur, card shadow, outer glow, any background behind speaker icons, extra controls, spelling errors, distorted text, device frame, watermark.
```

## 04 词典编辑风

```text
Use case: ui-mockup
Asset type: shippable Android vocabulary-learning popup concept, full-screen screenshot
Input image: Image 1 is the edit target.
Primary request: Redesign ONLY the existing white word-detail popup card in the lower-middle area into concept 04, a modern editorial dictionary layout. Keep the popup attached to exactly the same anchor point and keep roughly the same outer footprint.
Popup design: pure white card, restrained 10 dp corner radius, crisp cool-gray 1 dp border, absolutely no shadow, no elevation, no glow. Use generous left alignment and a slim blue vertical accent rule inside the card beside the linguistic content. Put the bold word title first. Show two tidy pronunciation rows with locale labels, phonetic text, and conventional dark navy loudspeaker icons aligned at the far right. Speaker icons must be directly on white with completely transparent backgrounds: no circle, square, pill, chip, fill, border, or button surface. Treat "n." as a small blue part-of-speech marker followed by the Chinese definition with a strong baseline. At the bottom, use a simple dark-navy text action with a small right chevron and a thin top divider, not a large filled button.
Text, render verbatim with no substitutions and no extra text:
"presence"
"美 /ˈprezəns/"
"英 /ˈprezəns/"
"n. 出席, 面前, 存在, 仪态, 风度"
"查看完整释义"
Typography: polished modern dictionary typography using Android sans-serif; title bold; tight optical alignment; exact Chinese and Latin characters.
Color palette: white, deep navy, cool gray, and restrained bright blue accent only.
Critical invariants: Change only the popup card and its contents. Preserve the complete page outside the card, status bar, top toolbar, underlying vocabulary content, gray 12 percent dim overlay, visible anchor marker, and bottom next-word button as closely as possible. The area outside the card must stay uniformly dimmed. No popup shadow anywhere.
Avoid: gradients, purple, decorative illustration, glass effects, blur, card shadow, outer glow, background behind speaker icons, extra controls, spelling errors, distorted text, device frame, watermark.
```

## 05 居中聚焦

```text
Use case: ui-mockup
Asset type: shippable Android vocabulary-learning popup concept, full-screen screenshot
Input image: Image 1 is the edit target.
Primary request: Redesign ONLY the existing white word-detail popup card in the lower-middle area into concept 05, a centered focus layout. Keep the popup attached to exactly the same anchor point and keep roughly the same outer footprint.
Popup design: pure white card, 16 dp corner radius, thin cool-light-gray 1 dp border, absolutely no shadow, no elevation, no glow. Center a larger bold "presence" title. Beneath it, place US and UK pronunciation side by side in one clean row, each group containing the locale, phonetic text, and a conventional dark navy loudspeaker icon. The two speaker icons must float directly on the white card with fully transparent backgrounds: no circle, square, pill, chip, fill, border, or button surface. Center the definition below with ample breathing room. Make "查看完整释义" a quiet navy outline action with a thin 1 dp stroke and 10 dp corners, not a heavy filled pill.
Text, render verbatim with no substitutions and no extra text:
"presence"
"美 /ˈprezəns/"
"英 /ˈprezəns/"
"n. 出席, 面前, 存在, 仪态, 风度"
"查看完整释义"
Typography: practical Android sans-serif; large confident title; compact phonetics; exact Chinese and Latin characters.
Color palette: white, deep navy, neutral gray, and restrained blue only.
Critical invariants: Change only the popup card and its contents. Preserve the complete page outside the card, status bar, top toolbar, underlying vocabulary content, gray 12 percent dim overlay, visible anchor marker, and bottom next-word button as closely as possible. The area outside the card must stay uniformly dimmed. No popup shadow anywhere.
Avoid: gradients, purple, decorative illustration, glass effects, blur, card shadow, outer glow, background behind speaker icons, oversized typography, extra controls, spelling errors, distorted text, device frame, watermark.
```

## 06 深色标题带

```text
Use case: ui-mockup
Asset type: shippable Android vocabulary-learning popup concept, full-screen screenshot
Input image: Image 1 is the edit target.
Primary request: Redesign ONLY the existing white word-detail popup card in the lower-middle area into concept 06, a dark-header dictionary popup. Keep the popup attached to exactly the same anchor point and keep roughly the same outer footprint.
Popup design: one flat card with a pure white body, 12 dp corner radius, thin cool-gray 1 dp outline, absolutely no shadow, no elevation, no glow. The top quarter is a solid deep-navy header band that follows the card's top corners, with no gradient. Put the bold white word title on the left of this band. On the right, place two compact locale labels with conventional white loudspeaker icons as pronunciation actions. Each speaker icon has a completely transparent background: no circle, square, pill, chip, fill, border, or button surface. In the white body, show the two phonetic strings with locale labels in a clean two-row block, then the definition. Finish with a compact deep-navy text action centered at the bottom, separated by a fine gray hairline; do not add another filled button.
Text, render verbatim with no substitutions and no extra text:
"presence"
"美"
"英"
"美 /ˈprezəns/"
"英 /ˈprezəns/"
"n. 出席, 面前, 存在, 仪态, 风度"
"查看完整释义"
Typography: practical Android sans-serif; bold white title in header; compact highly legible body; exact Chinese and Latin characters.
Color palette: solid deep navy, pure white, neutral gray, and a very small restrained blue accent only.
Critical invariants: Change only the popup card and its contents. Preserve the complete page outside the card, status bar, top toolbar, underlying vocabulary content, gray 12 percent dim overlay, visible anchor marker, and bottom next-word button as closely as possible. The area outside the card must stay uniformly dimmed. No popup shadow anywhere.
Avoid: gradients, purple, decorative illustration, glass effects, blur, card shadow, outer glow, any background behind speaker icons, extra controls, spelling errors, distorted text, device frame, watermark.
```

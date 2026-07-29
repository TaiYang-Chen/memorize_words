# 学习完成页概念图 - gpt-image-2

四个候选方案分别通过 `image_gen.py edit` 生成，使用 `gpt-image-2`、`quality=medium`，请求尺寸为 `1088x2400`。接口返回的原始文件保存在 `native/`，标准候选图使用 Lanczos 居中裁切并统一为 `1088x2400`。

| 编号 | 方案 | 标准图 | 最终提示词 |
| --- | --- | --- | --- |
| 01 | 数据聚焦 | `01-data-focus.png` | `prompts/01-data-focus.txt` |
| 02 | 编辑式报告 | `02-editorial-report.png` | `prompts/02-editorial-report.txt` |
| 03 | 伙伴庆祝 | `03-mascot-celebration.png` | `prompts/03-mascot-celebration.txt` |
| 04 | 荣誉横幅 | `04-honor-banner.png` | `prompts/04-honor-banner.txt` |

方案 01、02、04 使用当前完成页截图作为编辑目标。方案 03 额外使用项目中的 `feature_home_stats_hero_mascot_v3.png` 作为品牌伙伴参考。接口地址和密钥未写入任何产物。

总览图：`learning-done-concepts-overview.png`

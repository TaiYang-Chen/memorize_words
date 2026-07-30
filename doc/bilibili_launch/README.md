# Bilibili 首发制作包

这套文件把“炫羲单词”的首发视频拆成可执行的调研、录制、剪辑、投稿和内测招募流程。首发采用**播放优先版**：用约 1 分 25 秒先证明“已学词跟到刷手机的地方”，再在结尾招募 Android 内测。当前状态是 **研究与制作就绪、发布未就绪**：签名候选 APK、实机录屏、真人配音与最终封面仍需补齐。

## 文件索引

| 文件 | 用途 |
|---|---|
| [research.md](research.md) | 同类视频样本、平台边界、可复用方法与首发差异化判断 |
| [script.md](script.md) | 1 分 25 秒逐镜头拍摄与剪辑脚本 |
| [narration.txt](narration.txt) | 可直接照读的净旁白 |
| [subtitles.srt](subtitles.srt) | 与 1:25 脚本对齐的字幕 |
| [media-manifest.csv](media-manifest.csv) | 素材文件名、时长和验收标准 |
| [recording-guide.md](recording-guide.md) | 手机录屏、配音和隐私处理规范 |
| [publish-copy.md](publish-copy.md) | 标题、简介、置顶评论、私信和反馈模板 |
| [claim-check.md](claim-check.md) | 可宣传功能、证据入口与禁用口径 |
| [release-readiness.md](release-readiness.md) | 当前候选包阻塞、构建、验签和实机门槛 |
| [qa-checklist.md](qa-checklist.md) | 从改名到结束首轮招募的发布清单 |
| [release-info.template.txt](release-info.template.txt) | APK 和下载信息交付模板 |
| [validate.ps1](validate.ps1) | 草稿及发布前自动校验 |
| [cover/cover-template.html](cover/cover-template.html) | 1920 x 1080 可编辑封面源文件 |
| [cover/README.md](cover/README.md) | 封面替图与导出说明 |

## 最短执行路径

1. 确认 App 标题、入口、关于页、APK 展示名、账号资料和本目录公开文案均使用“炫羲单词”。
2. 按 [release-readiness.md](release-readiness.md) 解决版本、Release API 和 schema 2 角色服务端闭环，再构建签名候选包并填写 `release-info.txt`。不要使用 7 月 17 日的旧 APK。
3. 在实体 Android 手机按 [qa-checklist.md](qa-checklist.md) 完成全流程冒烟测试，并为脚本中的一镜到底实验预先选定一个真实已学词。
4. 按 [media-manifest.csv](media-manifest.csv) 录制素材，文件放入 `media/raw/`；`B01` 与 `B02` 必须保留为同一段连续原始录屏。
5. 按 [narration.txt](narration.txt) 录制 48 kHz WAV，再按 [script.md](script.md) 剪辑 1 分 25 秒成片并导入 [subtitles.srt](subtitles.srt)。
6. 将真实跨 App 截图保存为 `cover/cross-app-screenshot.png`，按 [cover/README.md](cover/README.md) 导出 `cover/cover-final.png`，并完成封面盲测。
7. 运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./doc/bilibili_launch/validate.ps1 -Mode Release`。只有返回 `PASS` 才进入投稿流程。

## 状态约定

- `Draft`：允许版本、下载地址和实机画面尚未补齐，但不允许出现夸大宣传、品牌占位符或旧品牌。
- `Release`：不允许任何占位符、旧品牌、草稿封面或缺失素材；APK 的实际 SHA-256 必须与交付信息一致。
- 统计镜头只使用真实操作形成的数据。为了保护正式账号，可使用演示账号，但画面必须标注“演示账号”。
- 本制作包不会自动上传视频、发送私信或对外发布 APK。

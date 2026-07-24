# Character Pack Tooling

这是一个独立于 Android/Gradle 的角色包制作工具。输入每个动作的视频或透明 PNG
序列，输出可供 schema 2 客户端安装的 KTX2/Basis 分页纹理包。

## 能做什么

- 视频通过 FFmpeg 解码为原始 RGBA 帧；非 30 FPS 视频在预乘 Alpha 空间离线插帧到
  30 FPS，不使用不均匀重复帧，也不会因插帧丢失透明通道。
- PNG 序列根据 `sourceFps` 对 RGBA 做时间插值，不使用不均匀重复帧。
- 全部动作共享同一套透明裁边、等比缩放和锚点几何，避免动作切换时大小或位置跳变，
  并扩张透明边缘 RGB。像素完全相同的动作边界帧会自动复用；当前绿色角色的 `idle`
  与 `card_open` 首帧相同，因此自动得到 117 帧而不是 118 帧，无需维护帧区间。
- 在 4×4、5×5、6×6 中选择显存浪费最小的网格，每页不超过 2016×2016。
- 优先调用 `toktx`，也支持 `basisu`，生成 UASTC + Zstd 的二维数组 KTX2。
- 生成 schema 2 `manifest.json`、预览 PNG、扁平 ZIP、构建元数据和 SHA-256。
- 标准动作固定使用 `standard` 纹理并执行 16 MiB 常驻显存预算检查；可选动作可放入
  独立纹理，便于运行时按需加载。单纹理上限 16 MiB，运行时总上限 32 MiB，ZIP
  上限 25 MiB；工具与客户端使用同一套限制。

此目录没有 `build.gradle`，也没有加入 `settings.gradle.kts`，不会参与 Android 编译。

## 安装依赖

需要 Python 3.10+：

```powershell
cd character-pack-tooling
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
```

外部程序：

1. 视频输入需要 FFmpeg/ffprobe，并且必须能够将输入解码为 RGBA PNG。
2. KTX2 编码器二选一：
   - 推荐 KTX-Software 4.x 的 `toktx`；
   - 或支持 KTX2、UASTC、Zstd 和 `2darray` multifile 模式的 `basisu`。
3. 必须使用仓库提供的 `basis_ktx2_validator` 做发布机全页转码验收。它直接编译与
   Android 客户端相同、固定 commit 的官方 Basis Universal 源码：

```powershell
cmake -S character-pack-tooling/host-validator `
  -B character-pack-tooling/host-validator/build `
  -DCMAKE_BUILD_TYPE=Release
cmake --build character-pack-tooling/host-validator/build --config Release
```

把程序加入 `PATH`，或通过 `--ffmpeg`、`--ffprobe`、`--encoder-path`、
`--validator-path` 指定绝对路径。工具不会自动下载二进制文件。validator 的版本标识
必须匹配仓库固定的 Basis commit，否则 `--check` 和正式构建都会拒绝继续。

## 输入目录

复制 [pack.example.yaml](pack.example.yaml) 到角色素材目录并改名为 `pack.yaml`：

```text
green_pet/
  pack.yaml
  card_open.mov
  card_close.mov
  tap.mov
  frames/
    idle/0001.png
    card_visible/0001.png ...
    favorite/0001.png ...
```

规则：

- 所有 `source` 必须是 `pack.yaml` 目录内的相对路径，禁止 `..` 和绝对路径；全部动作
  必须使用相同的源画布尺寸，以保持角色比例、锚点和动作边界一致。
- PNG 序列目录只能包含 PNG，按文件名中的数字自然排序；多帧序列必须声明
  `sourceFps`。
- 默认要求素材中确实存在透明像素。普通 MP4 通常没有 Alpha，应先转为透明 PNG、
  Alpha 视频，或在上游完成绿幕去除。确实需要不透明角色时才显式设置
  `target.requireAlpha: false`。
- `idle`、`card_open`、`card_visible`、`card_close` 必须存在并绑定同名 semantic；
  `idle` 为 `hold_last`，`card_visible` 为 `loop`，`card_open` 与 `card_close` 为可反向
  `once`，并分别连接 `card_visible` 与 `idle`。
- `next` 必须引用已有动作，循环动作不能设置 `next`；事件动作和非标准 semantic 动作
  必须是有限动画，在定义停止事件前不得使用 `loop`。
- 新增已有业务事件的动作只需增加素材和 `event` 映射。全新的业务事件仍需客户端首次
  增加事件发送点。
- 每个动作可用 `texture` 分组。四个标准动作必须位于 `standard`；建议把 `tap`、
  `favorite` 等可选动作放在 `optional` 或更细的独立纹理中。

## 使用

无需安装 FFmpeg/KTX 编码器的配置预览：

```powershell
python character_pack.py D:\assets\green_pet\pack.yaml --dry-run
```

严格检查素材和全部外部依赖，不生成文件：

```powershell
python character_pack.py D:\assets\green_pet\pack.yaml --check --encoder toktx `
  --validator-path D:\tools\basis_ktx2_validator.exe
```

构建发布包：

```powershell
python character_pack.py D:\assets\green_pet\pack.yaml --output dist --encoder toktx `
  --validator-path D:\tools\basis_ktx2_validator.exe
```

已有输出默认拒绝覆盖；确认要替换时使用 `--force`。排查素材时可添加 `--keep-work`
保留标准化帧和分页 PNG。

缺少依赖时命令会返回退出码 2，并明确指出缺少 `ffmpeg`、`ffprobe`、`toktx` 或
`basisu`、`basis_ktx2_validator`。`--dry-run` 永远不会启动这些程序。

## 输出

`dist/green_pet_v2/` 示例：

```text
manifest.json
standard.ktx2
optional.ktx2
green_pet_preview.png
green_pet_v2.zip
build-metadata.json
SHA256SUMS.txt
```

ZIP 采用固定时间戳和稳定顺序，且只包含 `manifest.json` 与 manifest 引用的 KTX2，
所有 entry 均在 ZIP 根目录。KTX2 已压缩，因此以 `STORED` 方式写入 ZIP。相同输入、
相同 Python/FFmpeg/编码器版本会得到可复现产物；升级外部编码器可能改变字节和 SHA，
必须提升 `packVersion`。

工具会读取并检查 KTX2 identifier、UASTC `vkFormat`、Zstd supercompression、尺寸、
数组层数、单 mip level 和所有数据范围；随后由官方 Basis validator 对每一页完整转码
ETC2 RGBA、ETC1 RGB 与 ETC1 Alpha。任一页、任一目标失败，或 DFD 不是 sRGB、
UASTC RGBA、straight Alpha、BT.709，均不会发布半成品目录。Android 安装器仍会再次
执行同样的全页三目标转码，形成制作端与设备端双重校验。

## 测试

单元测试不需要 FFmpeg 或 KTX 编码器：

```powershell
python -m unittest discover -s tests -v
```


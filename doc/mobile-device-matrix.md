# 手机竖屏设备矩阵

本文件是手机竖屏布局验收的证据索引，不是“按机型写布局”的清单。生产代码不得根据
`Build.MANUFACTURER`、型号、物理像素或厂商私有 API 选择布局。布局依据是实际运行时
窗口宽高、Insets、显示大小和字体倍率；资源限定符只能来自已验证的连续窗口宽度断点。

## 证据与命名规则

- 记录日期：2026-08-02（Asia/Shanghai）。官方搜索入口曾返回上游 `502`，以下记录改由各
  厂商的产品规格页直连核验；每行保留地区和访问日期，不能把一个地区版的规格外推到其他
  型号或地区版。
- 厂商页的物理面板参数仅用于选取验收设备。`screenWidthDp`、`screenHeightDp`、`densityDpi`、
  字体倍率、显示大小、导航方式和 Insets 都是 Android 运行时配置，厂商规格页通常不会发布，
  必须在对应真机上采集，禁止由 PPI 或物理像素反推。
- 分辨率以厂商页的写法保留在“官方原文”列；“竖屏物理 px”仅交换长短边，方便与 Android
  竖屏测试报告比对，不代表窗口大小。
- **iQOO 11S 更正：**官方参数页写明 `3200 x 1440`、`6.78 英寸`、`20:9`、最高 `144 Hz`；
  竖屏物理分辨率为 `1440 x 3200`。因此 `1080 x 2400 px / 360 x 800 dp / 480 dpi` 不能标为
  iQOO 11S 的官方目标，除非后续一台真实 iQOO 11S 的运行时采样另行证明其 Android 逻辑配置。

## 官方规格已核验（选机证据，非运行时覆盖）

下表覆盖 Samsung、Xiaomi、Redmi、Huawei、HONOR、OPPO、OnePlus、realme、vivo/iQOO 的
官方页面。它不表示应用已在这些真机上运行，也不产生任何基于品牌或型号的生产代码分支。
“约”表示由厂商公布的物理像素计算，非厂商声明的比例。

已核验的竖屏物理宽度族为 `720`、`1080`、`1220`、`1260`、`1280`、`1316` 和 `1440 px`，
并包含 `19.5:9`、`20:9`、`20.5:9` 及相邻连续比例。它们是测试选机边界，不是 Android
资源限定符，也不是设计稿宽度。

| 品牌族 | 型号 / 官方地区 | 系统版本 | 官方规格页 | 访问日期 | 官方原文分辨率 | 竖屏物理 px | 屏幕尺寸 / 比例 | 挖孔 / 默认导航 | 证据状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Samsung Galaxy | Galaxy S25 Ultra / AE (`SM-S938BZBOMEA`) | 待真机采集 | [Samsung](https://www.samsung.com/ae/smartphones/galaxy-s25-ultra/specs/) | 2026-08-02 | `3120 x 1440 (Quad HD+)` | `1440 x 3120` | `6.9` 英寸 / `19.5:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| Xiaomi | Xiaomi 15 Ultra / Global | 待真机采集 | [Xiaomi](https://www.mi.com/global/product/xiaomi-15-ultra/specs/) | 2026-08-02 | `3200 x 1440` | `1440 x 3200` | `6.73` 英寸 / `20:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| Redmi | Redmi Note 14 Pro 5G / Global | 待真机采集 | [Xiaomi](https://www.mi.com/global/product/redmi-note-14-pro-5g/specs/) | 2026-08-02 | `2712 x 1220` | `1220 x 2712` | `6.67` 英寸 / 约 `20:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| Redmi | REDMI A5 / Global | 待真机采集 | [Xiaomi](https://www.mi.com/global/product/redmi-a5/specs/) | 2026-08-02 | `1640 x 720` | `720 x 1640` | `6.88` 英寸 / `20.5:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| HUAWEI | Mate 70 Pro / CN | 待真机采集 | [HUAWEI](https://consumer.huawei.com/cn/phones/mate70-pro/specs/) | 2026-08-02 | `2832 x 1316` | `1316 x 2832` | `6.9` 英寸 / 约 `2.152:1` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| HUAWEI | nova 14 / CN | 待真机采集 | [HUAWEI](https://consumer.huawei.com/cn/phones/nova14/specs/) | 2026-08-02 | `2412 x 1084` | `1084 x 2412` | `6.7` 英寸 / 约 `20:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| HONOR | Magic7 Pro / Global | 待真机采集 | [HONOR](https://www.honor.com/global/phones/honor-magic7-pro/spec/) | 2026-08-02 | `1280 x 2800` | `1280 x 2800` | `6.8` 英寸 / 约 `19.7:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| HONOR | X8c / Global | 待真机采集 | [HONOR](https://www.honor.com/global/phones/honor-x8c/spec/) | 2026-08-02 | `2412 x 1080` | `1080 x 2412` | `6.7` 英寸 / 约 `20.1:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| OPPO | Find X8 Ultra / CN | 待真机采集 | [OPPO](https://www.oppo.com/cn/smartphones/series-find/find-x8-ultra/specs/) | 2026-08-02 | `3168 x 1440` | `1440 x 3168` | `6.82` 英寸 / `19.8:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| OnePlus | 13R / US | 待真机采集 | [OnePlus](https://www.oneplus.com/us/13r/specs) | 2026-08-02 | `2780 x 1264` | `1264 x 2780` | `6.78` 英寸 / 官方 `19.8:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| realme | C75 / Global | 待真机采集 | [realme](https://www.realme.com/global/realme-c75/specs) | 2026-08-02 | `2400 x 1080` | `1080 x 2400` | `6.72` 英寸 / `20:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| vivo / iQOO | iQOO 11S / CN | 待真机采集 | [vivo iQOO 参数页](https://www.vivo.com.cn/vivo/param/iqoo11s) | 2026-08-02 | `3200 x 1440` | `1440 x 3200` | `6.78` 英寸 / 官方 `20:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |
| vivo / iQOO | iQOO Z10 Turbo / CN | 待真机采集 | [vivo iQOO 参数页](https://www.vivo.com.cn/vivo/param/iqooz10turbo) | 2026-08-02 | `2800 x 1260` | `1260 x 2800` | `6.78` 英寸 / 官方 `20:9` | 待真机采集 | 官方物理规格已核验；真机未覆盖 |

尚未有对应真机运行时记录的机型都保持“真机未覆盖”。在拿到真机前，不得用表中物理像素宣称
`dp`、`densityDpi`、导航方式、挖孔 Insets 或默认显示大小已经适配完成。

## 运行时采集记录

每个候选机型必须在首次启动完成、系统栏稳定后运行 `RuntimeWindowMetricsReporterTest`，并把
测试输出中以 `MOBILE_WINDOW_METRICS` 开头的一行粘贴到对应记录。采集器只位于
`core-ui/src/androidTest`，不会进入正式 APK。

| 厂商 / 型号 | Android 版本 | 物理 px | WindowMetrics px | screenWidthDp x screenHeightDp | densityDpi | fontScale | 显示大小 | 导航模式 | 状态栏 / 导航栏 / 挖孔 / 手势 / IME Insets | 截图报告 | 状态 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| iQOO 11S / CN 真机 | 待采集 | `1440 x 3200`（官方物理规格） | 待采集 | 待实际启动采集 | 待实际启动采集 | 待采集 | 待采集 | 待采集 | 待采集 | 待采集 | 未覆盖 |
| 通用 FHD+ 20:9 Android 测试配置（Google API 36 模拟器） | API 36 | `1080 x 2400` | `1080 x 2400` | `360 x 752` | `480` | `1.0` | 默认 | 三键导航 | 状态栏 `0,72,0,0`；导航栏 `0,0,0,72`；挖孔 `0,0,0,0`；手势 `90,72,90,96`；IME `0,0,0,0` | 采集器 instrumentation 输出 | 仅验证通用窗口处理 |

运行命令：

```powershell
.\gradlew.bat :core-ui:connectedDebugAndroidTest
```

### 当前模拟器采样（不计入厂商真机覆盖）

`2026-08-02` 在名为 `iqoo11s(AVD)` 的模拟器上运行通过。其名称不是设备身份；实际 Build 标识为
`Google / sdk_gphone64_x86_64`，API 36。该通用测试配置的采样结果为：物理与窗口均为 `1080 x 2400 px`，
`360 x 752 dp`，`480 dpi`，`fontScale=1.0`，`navigationMode=navigation-bar-visible`
（`settings secure navigation_mode=2`）。Insets 分别为：状态栏 `0,72,0,0`、导航栏
`0,0,0,72`、挖孔 `0,0,0,0`、系统手势 `90,72,90,96`、IME `0,0,0,0`。这条记录只验证
采集器和通用窗口处理，不用于宣称 iQOO 11S 或任一厂商机型已覆盖。

每台代表设备至少采集以下组合：默认显示大小与较大显示大小、`fontScale 1.0 / 1.3`、手势与
三键导航、挖孔状态，以及显示键盘后的 IME Insets。每张页面截图都应关联同一行运行时参数。

### 模拟屏幕档位回归（不计入厂商真机覆盖）

`2026-08-02` 在同一 API 36 手机模拟器上临时切换以下显示配置，并运行
`RuntimeWindowMetricsReporterTest`。每档都断言内容根节点只保留一次系统栏、导航栏、挖孔和
IME 的最大安全边距；测试结束后已恢复 `1080 x 2400 px / 480 dpi`。

| 屏幕族 | 物理 px / densityDpi | 运行时 screenDp | 导航栏 Insets | 结果 |
| --- | --- | --- | --- | --- |
| HD+ 20:9 | `720 x 1600` / `320` | `360 x 752` | `0,0,0,48 px` | 通过 |
| FHD+ 20:9（指定档位） | `1080 x 2400` / `480` | `360 x 752` | `0,0,0,72 px` | 通过 |
| 1.5K 约 20:9 | `1220 x 2712` / `500` | `390 x 820` | `0,0,0,75 px` | 通过 |
| QHD+ 19.5:9 | `1440 x 3200` / `560` | `411 x 866` | `0,0,0,84 px` | 通过 |

这里的 `screenHeightDp` 是三键导航栏避让后的应用内容高度；物理面板按 density 换算的总逻辑高度
比它多 `24 dp`。这是 Android 的正确可用布局空间，不能为了凑整而让底部控件落到系统导航栏下方。

## Insets 架构规则

- 所有应用内手机 Activity 通过 `PhoneEdgeToEdgeActivity` 统一开启 edge-to-edge、设置 API 25-29
  兼容的 IME resize 行为，并在 `setContentView` 后只向实际内容根节点派发一次 Insets 策略。
- 默认内容根只避让系统栏、显示挖孔和 IME；系统手势 Insets 不是普通内容留白。手势禁放区仅由
  悬浮复习等可拖拽浮层显式使用，避免无意义地压缩常规页面宽度。
- 固定底栏可通过 Activity 的 `applyContentWindowInsets` 覆盖策略独立拥有底部 Insets；其余页面不得
  再自行注册窗口监听器或叠加 padding/margin。
- 普通 Dialog 和 Material BottomSheet 保持各自 window decor 的系统栏处理，只统一设置 IME resize。
  不向其内容根额外添加导航栏 padding，以免在不同 Material/Android 版本上重复计算。
- Insets API 以 `START/END` 语义映射布局方向；RTL 下会自动交换物理左右边缘。

## 验收边界

- 验证 API 25、30、36；覆盖引导、首页、单词书、学习/练习、个人中心、悬浮复习和通用弹窗。
- 检查标题、列表、图片、底部操作、弹窗和输入区不截断、不重叠、不越界。
- 本次仅验收手机竖屏。不增加横屏、平板、分屏或折叠展开的信息架构。

# Bilibili 首发候选包发布就绪门禁

本文用于判断一个 APK 是否可以同时作为 Bilibili 录屏包和首轮 50 人内测包。结论以同一份签名 Release APK 为准，Debug APK、旧归档包和仅在模拟器运行过的构建均不能替代。

核对基线：2026-07-23。当前状态为 **NO-GO**。

## 1. 当前硬阻塞

1. **品牌已锁定为“炫羲单词”，仍待候选包验证。** 桌面名称、首页、关于页、版权、协议、更新通知和宣传材料均应使用该名称；本轮不迁移既有域名或下载地址。构建公开候选包前，必须用签名 Release APK 和实机录屏复核这些可见文案。
2. **版本仍为 `versionCode = 1`、`versionName = "1.0"`。** 新候选包的 `versionCode` 必须高于所有曾分发版本，`versionName` 必须能唯一对应本轮内测；不得只改文件名或 SHA-256。
3. **Release API 地址未配置。** Release 构建要求显式提供 `memorize.releaseApiBaseUrl`，地址必须使用 HTTPS 且以 `/` 结尾。当前仓库级、用户级 Gradle 配置及相关环境变量中均未发现可用值，直接执行 Release 构建会失败。
4. **现有正式包早于当前源码。** `release/generated/` 中 APK/AAB 的时间为 2026-07-17；当前 HEAD 为 2026-07-20，且角色包 schema 2、KTX2/Basis GPU 渲染、下载校验和悬浮服务仍有一组已暂存改动。旧 APK 不包含当前实现，禁止录屏或分发。
5. **当前没有 Release 原生构建证据。** `app/build/outputs/` 下没有当前 Release APK/AAB，`core-sprite-animation` 也没有当前 Release CMake 输出。现有 `app-debug.apk` 使用调试证书且只打包 `x86_64`，不可用于 ARM 手机内测。
6. **schema 2 角色包尚未形成可验证的服务端闭环。** 旧 schema 1 发布资料正被删除；新包目前只存在于被 Git 忽略的 `build/` 目录。新客户端只声明支持 manifest schema `2` 和 renderer `ktx2_paged_v2`，新安装用户又没有内置角色包，因此必须先发布兼容的 HTTPS catalog/resolve 数据和角色资源。
7. **角色升级相关测试证据过期。** `feature-floating-review`、`feature-home`、`feature-learning` 的现有报告为 2026-07-23 且无失败；但 `core-sprite-animation`、`data`、`domain` 报告仍为 2026-07-19，早于当前角色/GPU 改动，必须全部重跑。

## 2. 当前可用构建条件

| 项目 | 已核对状态 |
|---|---|
| Android 配置 | `applicationId = com.chen.memorizewords`，compile/target SDK 36，min SDK 25（Android 7.1） |
| 构建工具 | Gradle 8.13、AGP 8.12.3、JDK 21、Android Build Tools 36.0.0 |
| 原生工具链 | CMake 3.22.1 已安装，Android NDK 已安装；Debug 原生产物覆盖 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` |
| Release 签名 | `RELEASE_STORE_FILE`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` 均已在本机配置，keystore 文件存在 |
| Release 证书 | 公开 SHA-256 指纹为 `4021f9768f9198da2d984058c2bb166e5b1dfc81ea39def50d1a8970ae196a63`，有效期至 2053-11-26 |
| 归档任务 | `:app:archiveReleaseArtifacts` 会清理旧归档，构建 APK/AAB，并输出 mapping 与 `SHA256SUMS.txt` 到 `release/generated/` |
| 当前 schema 2 包 | `green_pet`，版本 `26072001`，ZIP 5,486,808 字节，117 帧、5 页，估算常驻 GPU 内存 14,112,000 字节；本地清单 SHA-256 已全部匹配 |

签名口令和私钥不得写入本文、命令行、提交记录、截图、录屏或 `release-info.txt`。签名值只允许保存在已忽略的本机配置或受控 CI Secret 中。

## 3. 出包前必须锁定的输入

### 3.1 品牌与版本

- 产品中文名已固定为“炫羲单词”。桌面名称、启动入口、首页标题、关于页、版权、协议、更新通知及本目录全部发布文案均应使用该名称；既有域名和下载地址在本轮不迁移。
- 软件包名是否保留应单独记录。若不做迁移，保持 `com.chen.memorizewords`；不要仅因品牌改名临时更换包名。
- 将 `versionCode` 设置为高于所有历史分发包的整数；将 `versionName` 设置为本轮唯一、可对用户解释的版本，例如正式确定的 beta 版本号。
- 候选包必须从已审查、已提交且可复现的 Git 提交构建。构建前 `git status --porcelain` 应为空，并将 `git rev-parse HEAD` 写入 `release-info.txt`。

检查最终品牌、残留展示名和版本：

```powershell
rg -n 'Memorize Words|\[App 新名称\]' `
  --glob '!**/lint-baseline.xml' `
  --glob '!release-readiness.md' `
  --glob '!validate.ps1' `
  --glob '!**/build/**' `
  --glob '!**/.gradle/**' `
  app feature-feedback feature-home domain-sync doc/bilibili_launch

rg -n -F '炫羲单词' `
  --glob '!**/lint-baseline.xml' `
  --glob '!**/build/**' `
  --glob '!**/.gradle/**' `
  app feature-feedback feature-home domain-sync doc/bilibili_launch

rg -n 'versionCode|versionName' app/build.gradle.kts
git status --short
git rev-parse HEAD
```

### 3.2 Release API

- 确定真实、可长期使用的 Release API 根地址。
- 地址必须为 `https://`，必须以 `/` 结束，并应包含 Retrofit 所需的最终 API 前缀，例如 `https://example.com/api/`。
- 从目标网络环境验证登录、词书、学习同步、权益、云评分、应用更新及角色包接口；不得把 Debug 的 HTTP IP 地址带入内测包。
- Release API 只作为本次 Gradle 参数或受控 CI 配置传入，不提交含鉴权参数的 URL。

### 3.3 schema 2 角色包服务端闭环

当前待发布角色包的 catalog 核心字段必须与本地产物一致：

| 字段 | 必须值/规则 |
|---|---|
| `packId` | `green_pet` |
| `packVersion` | `26072001` |
| `manifestSchemaVersion` | `2` |
| `packageSha256` | `ee6bbb1bad04a9d31d370da252fcbfc7fe04eecd3785d32d24eb7d43ea7b2a65` |
| `packageSizeBytes` | `5486808` |
| `previewUrl` / `packageUrl` | 可公开访问的 HTTPS URL；不得带用户名、密码或 fragment |
| 默认项 | 完整 catalog 中必须且只能有一个 `isDefault = true` |

服务端必须完成以下验收：

- `GET app/character-packs?supportedManifestSchemas=2&supportedRenderers=ktx2_paged_v2` 返回完整、唯一 packId、且恰有一个默认项的 catalog。
- `POST app/character-packs/resolve?supportedManifestSchemas=2&supportedRenderers=ktx2_paged_v2` 对新账号返回兼容包，对已有账号返回已应用包或明确的重新选择状态。
- `PUT app/character-packs/applied` 后再次 resolve 能返回新选择。
- ZIP 下载内容与 catalog 的大小和 SHA-256 一致；若服务器发送 `Content-Length`，其值必须等于 `5486808`。
- ZIP 不超过 25 MiB，manifest 不超过 128 KiB；包内只包含根目录 `manifest.json` 及 manifest 引用的 KTX2 文件。
- 真实设备完成首次下载、安装时全页转码、启动渲染；断网重启后继续使用已安装角色。

## 4. 测试与构建命令

以下命令均从仓库根目录执行。任何一步失败都应停止出包，不得复用上一次生成的文件。

### 4.1 角色包工具测试

```powershell
Push-Location character-pack-tooling
python -m unittest discover -s tests -v
Pop-Location
```

使用正式素材重新制作角色包时，还必须按 `character-pack-tooling/README.md` 对真实 `pack.yaml` 执行 `--check`，并使用仓库提供的 `basis_ktx2_validator` 完成全页转码校验。不得只验证 ZIP 能解压。

### 4.2 受影响模块单元测试

```powershell
.\gradlew.bat `
  :core-sprite-animation:testDebugUnitTest `
  :data:testDebugUnitTest `
  :domain:test `
  :feature-floating-review:testDebugUnitTest `
  :feature-home:testDebugUnitTest `
  :feature-learning:testDebugUnitTest `
  :app:testDebugUnitTest
```

随后运行静态检查和各模块的架构边界检查：

```powershell
.\gradlew.bat `
  :core-sprite-animation:check `
  :data:check `
  :domain:check `
  :feature-floating-review:check `
  :feature-home:check `
  :feature-learning:check `
  :app:check
```

保存命令输出、测试报告时间和构建提交号。已有旧报告不能代替本次结果。

### 4.3 签名 Release 归档

先把下方变量替换为正式 HTTPS API 地址；不要在命令中传入任何签名口令：

```powershell
$releaseApiBaseUrl = 'https://<正式 API 域名>/api/'

if (-not $releaseApiBaseUrl.StartsWith('https://') -or -not $releaseApiBaseUrl.EndsWith('/')) {
    throw 'Release API must use HTTPS and end with /'
}

.\gradlew.bat `
  "-Pmemorize.releaseApiBaseUrl=$releaseApiBaseUrl" `
  :app:archiveReleaseArtifacts
```

成功后必须同时存在：

```text
release/generated/xuanxi_words-release.apk
release/generated/xuanxi_words-release.aab
release/generated/mapping.txt
release/generated/SHA256SUMS.txt
```

## 5. 校验与验签命令

### 5.1 自动核对归档 SHA-256

```powershell
$archiveDir = Resolve-Path 'release/generated'
$expected = @{}

Get-Content (Join-Path $archiveDir 'SHA256SUMS.txt') | ForEach-Object {
    if ($_ -notmatch '^([0-9a-fA-F]{64})\s+(.+)$') {
        throw "Malformed checksum line: $_"
    }
    $expected[$matches[2].Trim()] = $matches[1].ToLowerInvariant()
}

foreach ($name in @('xuanxi_words-release.apk', 'xuanxi_words-release.aab', 'mapping.txt')) {
    $path = Join-Path $archiveDir $name
    $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected[$name]) {
        throw "SHA-256 mismatch: $name"
    }
}

'PASS: release archive checksums match'
```

### 5.2 验证 APK 签名、品牌、版本和 ABI

下面的 SDK 路径从已忽略的 `local.properties` 读取，不会打印签名配置：

```powershell
$sdkValue = ((Get-Content local.properties | Where-Object { $_ -match '^sdk\.dir=' }) `
    -replace '^sdk\.dir=', '')
$sdkDir = $sdkValue -replace '\\:', ':' -replace '\\\\', '\'
$buildTools = Join-Path $sdkDir 'build-tools/36.0.0'
$apk = Resolve-Path 'release/generated/xuanxi_words-release.apk'

$signatureReport = & (Join-Path $buildTools 'apksigner.bat') `
    verify --verbose --print-certs $apk 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    throw $signatureReport
}
$signatureReport

$releaseCert = '4021f9768f9198da2d984058c2bb166e5b1dfc81ea39def50d1a8970ae196a63'
if ($signatureReport -notmatch [regex]::Escape($releaseCert)) {
    throw 'APK was not signed by the expected release certificate'
}

$badging = & (Join-Path $buildTools 'aapt.exe') dump badging $apk
$badging | Select-String '^(package|sdkVersion|targetSdkVersion|application-label:|native-code)'

$nativeEntries = @(tar -tf $apk | Select-String '^lib/.+/libsprite_basis\.so$' | ForEach-Object Line)
foreach ($requiredEntry in @(
    'lib/arm64-v8a/libsprite_basis.so',
    'lib/armeabi-v7a/libsprite_basis.so'
)) {
    if ($nativeEntries -notcontains $requiredEntry) {
        throw "Missing required native library: $requiredEntry"
    }
}

'PASS: release signature and required native libraries verified'
```

人工检查 `aapt` 输出必须满足：

- `application-label` 是最终新品牌，不是旧名或占位符。
- `versionCode` 和 `versionName` 等于 `release-info.txt`。
- `sdkVersion` 为 `25`，`targetSdkVersion` 为 `36`。
- APK 至少支持本轮测试手机实际使用的 ABI；不得因第三方依赖带有 legacy `armeabi` 就误认为 `libsprite_basis.so` 也覆盖该 ABI。

最后将 APK 实际 SHA-256、版本、提交号和固定下载地址填入 `release-info.txt`，再从下载地址重新下载一次并复算 SHA-256。只有上传前后完全一致的文件才能发给测试者。

## 6. 实机发布门槛

至少使用一台接近最低版本的 Android 7.1+ 设备和一台当前主流 ARM64 设备。两台设备都安装同一份已验签 Release APK；不要用覆盖安装 Debug 包代替干净安装。

- [ ] 干净安装、启动、登录、选书和学习计划均成功；拒绝权限后 App 不崩溃。
- [ ] 每日签到真实获得 1 天桌宠体验，权益过期提示准确。
- [ ] 悬浮窗授权、跨 App 显示、拖动、贴边、卡片开关、换词、收藏、复制和详情入口均正常。
- [ ] schema 2 角色首次下载成功，安装阶段完成 KTX2 全页转码；退出、重启及断网后仍能显示。
- [ ] ARM64 与 ARMv7 目标设备加载 `libsprite_basis.so` 时无 `UnsatisfiedLinkError`，无持续黑帧、透明异常或显存导致的崩溃。
- [ ] 主学习故意答错后错词再次出现，完成后学习记录与复习状态真实更新。
- [ ] 听力、跟读、拼写、随身听均可完整结束；后台播放真实持续，云评分只展示服务端真实结果。
- [ ] 周趋势、学习日历和每日明细与真实记录一致。
- [ ] 飞行模式完成一次学习后数据仍在；恢复网络后待同步记录自动重试成功。
- [ ] 下载地址在非开发机网络可访问，APK 下载后的 SHA-256 与 `release-info.txt` 一致。
- [ ] 同一候选包完成至少 30 分钟稳定性使用，桌宠跨多个 App 期间无崩溃、ANR 或持续高温。

录屏必须在通过上述门槛的候选包上进行。录屏后若代码、角色包、服务端 catalog、版本号或签名 APK 任一发生变化，原候选结论立即失效，需重新构建并至少重做受影响测试、验签和实机冒烟。

## 7. 最终 GO 条件

只有同时满足以下条件，状态才可从 **NO-GO** 改为 **GO**：

1. 品牌、版本和 Release HTTPS API 已锁定，仓库不存在旧品牌或发布占位符。
2. schema 2 catalog、resolve、下载、安装和离线使用在真实账号与真实设备上闭环通过。
3. 本文列出的测试、静态检查、Release 构建、SHA-256、签名、版本、品牌和 ABI 检查全部通过。
4. `release-info.txt` 已完整填写，构建提交可追溯，下载后文件与本地归档完全一致。
5. [qa-checklist.md](qa-checklist.md) 全部完成，`./doc/bilibili_launch/validate.ps1 -Mode Release` 返回 `PASS`。


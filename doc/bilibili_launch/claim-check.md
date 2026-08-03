# 功能宣传证据与边界

这张表用于录制前核对口径。代码只能证明功能设计和实现路径，最终成片还必须用当前签名候选包在实体手机上验证。首发只围绕“已学词在其他 App 上出现”的结果展开，不把功能数量当成卖点。

| 主题 | 可以说 | 代码证据入口 | 不要说 |
|---|---|---|---|
| 首发核心承诺 | 词书中真实已学词可由桌宠卡片展示在其他 App 上层；演示需从选词到卡片打开连续录制 | [`FloatingWordService.kt`](../../feature-floating-review/src/main/java/com/chen/memorizewords/feature/floatingreview/ui/floating/FloatingWordService.kt)、[`FloatingReviewFacade.kt`](../../domain-floating/src/main/java/com/chen/memorizewords/domain/floating/service/FloatingReviewFacade.kt) | 不说“刚学过”除非录屏已证明；不说词从当前屏幕、视频或当前 App 得来 |
| 演示机制 | “词源：已学词”“不读屏”；演示词可来自当前词书的真实已学词或自选词 | [`FloatingReviewFacade.kt`](../../domain-floating/src/main/java/com/chen/memorizewords/domain/floating/service/FloatingReviewFacade.kt) | 不说桌宠识别当前 App、读屏或按刷到的内容推荐单词 |
| 跨 App 桌宠 | 可浮在其他 App 上层；可拖动、贴边；卡片支持换词、收藏、复制和详情入口 | [`FloatingWordService.kt`](../../feature-floating-review/src/main/java/com/chen/memorizewords/feature/floatingreview/ui/floating/FloatingWordService.kt) | 不说开机自启、悬浮卡朗读、无需权限 |
| 桌宠设置 | 当前词书或自选单词；随机与记忆曲线推荐等顺序；字段、大小和透明度可调 | [`FloatingReviewSettingsViewModel.kt`](../../feature-floating-review/src/main/java/com/chen/memorizewords/feature/floatingreview/ui/settings/FloatingReviewSettingsViewModel.kt) | 不把尚未开放的选项拍入首发 |
| 主学习 | 当前是看释义选单词；错词重新穿插；答题更新复习状态和间隔 | [`LearningViewModel.kt`](../../feature-learning/src/main/java/com/chen/memorizewords/feature/learning/ui/learning/LearningViewModel.kt)、[`Sm2Scheduler.kt`](../../domain-study/src/main/java/com/chen/memorizewords/domain/study/model/progress/word/Sm2Scheduler.kt) | 不说主学习可自由切换听力或拼写；不说全部任务都严格按 SM-2 到期生成 |
| 听力、跟读、拼写、随身听 | 可作为简介和后续选题的已实现专项练习；跟读支持录音、原声与自录回放，联网可提交云端评分 | [`ListeningPracticeViewModel.kt`](../../feature-learning/src/main/java/com/chen/memorizewords/feature/learning/ui/practice/ListeningPracticeViewModel.kt)、[`ShadowingPracticeViewModel.kt`](../../feature-learning/src/main/java/com/chen/memorizewords/feature/learning/ui/practice/ShadowingPracticeViewModel.kt)、[`SpellingPracticeViewModel.kt`](../../feature-learning/src/main/java/com/chen/memorizewords/feature/learning/ui/practice/SpellingPracticeViewModel.kt)、[`AudioLoopPlaybackService.kt`](../../feature-learning/src/main/java/com/chen/memorizewords/feature/learning/ui/practice/audioLoop/AudioLoopPlaybackService.kt) | 首发不逐项念菜单；不说精准纠音、手写识别或未经实机验证的后台时长 |
| 本地与同步 | 学习记录先落本地；联网后由待同步队列和 Worker 重试 | [`FailedSyncEventStore.kt`](../../data-sync/src/main/java/com/chen/memorizewords/data/sync/repository/sync/FailedSyncEventStore.kt)、[`SyncRetryWorker.kt`](../../data-sync/src/main/java/com/chen/memorizewords/data/sync/repository/sync/SyncRetryWorker.kt) | 不说全部数据实时云同步或全功能离线 |
| 使用条件 | Android 7.1+；桌宠需要悬浮窗权限和有效体验权益；每日签到可领 1 天体验；云评分有每日次数限制 | [`build.gradle.kts`](../../app/build.gradle.kts)、[`LearningCheckInViewModel.kt`](../../feature-learning/src/main/java/com/chen/memorizewords/feature/learning/ui/checkin/LearningCheckInViewModel.kt)、[`MembershipRepositoryImpl.kt`](../../data-sync/src/main/java/com/chen/memorizewords/data/sync/repository/membership/MembershipRepositoryImpl.kt) | 不包装成无条件免费 Pro；不展示未开放支付能力 |

## 成片禁区

- 不在首发中做听力、跟读、拼写、随身听、统计和同步的功能巡展；这些内容只在简介或后续视频中出现。
- 真题内容、二维码、会员支付、静态排名、词表发音和未开放的学习模式不进入首发。
- 跟读镜头如在后续视频使用，只保留真实云端响应；本地占位得分、调试数据和固定文案不能当作能力证明。
- 桌宠离线能力只限定为“角色下载完成后可离线运行”。首次角色下载、词书、自建词书和云评分仍需要网络。
- 不使用“神器”“首创”“吊打”“永久免费”“精准 AI”等无法证明的广告词。

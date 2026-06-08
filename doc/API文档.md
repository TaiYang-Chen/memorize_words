# API 文档

> 本文基于当前仓库 Retrofit 契约整理，覆盖 `:data` 聚合源码目录和 `:speech` 模块中的远程接口。接口以代码当前实现为准。

## 1. 通用约定

| 项 | 说明 | 源码 |
|---|---|---|
| Base URL | `http://10.130.56.105:8080/api/` | `data-sync/.../remoteapi/GlobalConfig.kt` |
| 统一响应 | `ApiResponse<T> { data: T?, code: Int, message: String }` | `core-network/.../http/ApiResponse.kt` |
| 分页响应 | `PageData<T> { items: List<T>, page: Int, size: Int, total: Long }` | `core-network/.../http/ApiResponse.kt` |
| 鉴权 | 除 `auth/login`、`auth/register`、`auth/refresh`、`auth/sms/send-code` 外，默认添加 `Authorization: Bearer <token>` | `core-network/.../CoreNetworkRoutePolicy.kt` |
| 请求格式 | 普通接口为 JSON；头像和反馈图片为 `multipart/form-data` | 各 `*ApiService.kt` |

源码归档说明：

- `data/build.gradle.kts` 将 `data-account`、`data-feedback`、`data-practice`、`data-sync`、`data-wordbook` 等目录纳入 `:data` 源码。
- `data-wordbook/.../UserDataSyncApiService.kt` 与 `data-sync/.../UserDataSyncApiService.kt` 当前接口内容重复；文档按 `data-sync` 口径记录，并视作同一组接口。
- `data-word/.../WordBookApiService.kt` 目录中也存在同名词书接口，但当前未被 `settings.gradle.kts` 或 `data/build.gradle.kts` 纳入构建；文档按已纳入 `:data` 的 `data-wordbook` 口径记录。
- `speech/.../PracticeApiService.kt` 与 `data-practice/.../PracticeApiService.kt` 当前语音练习接口内容重复；文档按 `data-practice` 口径记录，并注明 `speech` 复用。

## 2. 账号认证与资料

源码：`data-account/.../remoteapi/api/auth/AuthApiService.kt`

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `POST` | `auth/login` | 登录，支持密码、短信、OAuth 等方式 | 否 | `LoginRequest` | `ApiResponse<LoginResponseDto>` |
| `POST` | `auth/register` | 手机号注册 | 否 | `RegisterRequest` | `ApiResponse<LoginResponseDto>` |
| `POST` | `auth/sms/send-code` | 发送短信验证码 | 否 | `SendSmsCodeRequest` | `ApiResponse<SendSmsCodeResponseDto>` |
| `POST` | `auth/refresh` | 刷新登录态 | 否 | `RefreshRequest` | `ApiResponse<LoginResponseDto>` |
| `GET` | `me` | 获取当前用户资料 | 是 | 无 | `ApiResponse<ProfileDto>` |
| `POST` | `auth/logout` | 退出登录 | 是 | 无 | `ApiResponse<Unit>` |
| `POST` | `me/account/delete` | 删除账号 | 是 | 无 | `ApiResponse<Unit>` |
| `POST` | `auth/change-password` | 修改密码 | 是 | `ChangePasswordRequest` | `ApiResponse<Unit>` |
| `POST` | `auth/bind-social` | 绑定微信/QQ 等第三方账号 | 是 | `BindSocialRequest` | `ApiResponse<ProfileDto>` |
| `POST` | `upload/avatar` | 上传头像 | 是 | multipart `file` | `ApiResponse<AvatarUploadDto>` |
| `PATCH` | `me/profile` | 更新资料字段 | 是 | `Map<String, String>` | `ApiResponse<ProfileDto>` |

主要 DTO：

| DTO | 字段 |
|---|---|
| `LoginRequest` | `loginMethod`, `emailOrPhone?`, `phone?`, `password?`, `smsCode?`, `oauthCode?`, `platform?`, `state?` |
| `RegisterRequest` | `phone`, `password`, `registerMethod` |
| `SendSmsCodeRequest` | `phone`, `scene` |
| `RefreshRequest` | `refreshToken` |
| `ChangePasswordRequest` | `oldPassword`, `newPassword` |
| `BindSocialRequest` | `platform`, `oauthCode`, `state?` |
| `LoginResponseDto` | `token`, `refreshToken`, `tokenType`, `user`, `expiresIn`, `refreshTokenExpiresIn`, `onboarding?` |
| `UserDto` | `id`, `email?`, `nickname?`, `gender?`, `avatarUrl?`, `phone?`, `qq?`, `wechat?`, `emailVerified` |
| `ProfileDto` | `userId`, `email?`, `nickname?`, `gender?`, `avatarUrl?`, `phone?`, `qq?`, `wechat?`, `emailVerified` |
| `AvatarUploadDto` | `url` |

`PATCH me/profile` 的字段 key 来自 `ProfilePatchRequest.Field`：`nickname`、`gender`、`phone`、`wechat`、`qq`、`avatarUrl`。

## 3. 词书与查词

源码：`data-wordbook/.../remoteapi/api/wordbook/WordBookApiService.kt`

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `GET` | `wordbook/list` | 获取词书列表 | 是 | 无 | `ApiResponse<List<WordBookDto>>` |
| `POST` | `wordbook/words` | 分页获取词书单词 | 是 | `WordBookWordsRequest` | `ApiResponse<PageData<WordDto>>` |
| `POST` | `wordbook/lookup` | 按单词查询词条 | 是 | `WordLookupRequest` | `ApiResponse<WordDto?>` |

主要 DTO：

| DTO | 字段 |
|---|---|
| `WordBookWordsRequest` | `bookId`, `page`, `count` |
| `WordLookupRequest` | `word`, `normalizedWord` |
| `WordBookDto` | `id`, `title`, `category`, `imgUrl`, `description`, `totalWords`, `learnedWords`, `contentVersion`, `updatedAt`, `isNew`, `isHot`, `isSelected`, `isPublic`, `createdByUserId?` |
| `WordDto` | `id`, `word`, `normalizedWord`, `phoneticUS?`, `phoneticUK?`, `hasIrregularForms`, `memoryTip?`, `mnemonicImageUrl?`, `memoryAssociations`, `wordFamily?`, `synonyms`, `antonyms`, `tags`, `notes?`, `rootMemoryTip?`, `definitionDtos`, `exampleDtos`, `wordFormDtos`, `rootWords` |
| `WordDefinitionDto` | `id`, `wordId`, `partOfSpeech`, `definition` |
| `WordExampleDto` | `id`, `wordId`, `definitionId?`, `englishSentence`, `chineseTranslation?`, `difficultyLevel` |
| `WordFormDto` | `id`, `wordId`, `formWordId?`, `formType`, `formText` |
| `WordRootDto` | `id`, `rootWord`, `coreMeaning`, `etymology?`, `sourceLanguage`, `difficulty`, `tags?` |
| `RootWordDto` | `wordId`, `rootId`, `context`, `partOfSpeech`, `sequence` |
| `RootMeaningDto` | `id`, `rootId`, `meaning`, `examples` |
| `RootExampleDto` | `id`, `meaningId`, `exampleSentence`, `translation` |
| `RootVariantDto` | `id`, `rootId`, `variant` |
| `WordStateDto` | `wordId`, `bookId`, `totalLearnCount`, `lastLearnTime`, `nextReviewTime`, `masteryLevel`, `userStatus`, `repetition`, `interval`, `efactor` |

## 4. 用户学习同步

源码：`data-sync/.../remoteapi/api/datasync/UserDataSyncApiService.kt`

### 4.1 计划、引导与词书

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `GET` | `me/study-plan` | 获取学习计划 | 是 | 无 | `ApiResponse<StudyPlanDto?>` |
| `PUT` | `me/study-plan` | 更新学习计划 | 是 | `StudyPlanDto` | `ApiResponse<Unit>` |
| `GET` | `me/onboarding` | 获取首次引导状态 | 是 | 无 | `ApiResponse<OnboardingStateDto?>` |
| `PUT` | `me/onboarding` | 更新首次引导状态 | 是 | `OnboardingStateDto` | `ApiResponse<Unit>` |
| `GET` | `me/wordbooks` | 获取我的词书 | 是 | 无 | `ApiResponse<List<WordBookDto>>` |
| `POST` | `me/wordbooks` | 添加我的词书 | 是 | `AddMyWordBookRequest` | `ApiResponse<Unit>` |
| `PUT` | `me/wordbooks/{bookId}/selection` | 设置当前词书选择状态 | 是 | path `bookId`; body `WordBookSelectionSyncRequest` | `ApiResponse<Unit>` |

### 4.2 单词状态、收藏与进度

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `GET` | `me/wordbooks/{bookId}/word-states` | 分页获取词书单词学习状态 | 是 | path `bookId`; query `page`, `count` | `ApiResponse<PageData<WordStateDto>>` |
| `PUT` | `me/wordbooks/{bookId}/word-states/{wordId}` | 新增或更新单词学习状态 | 是 | path `bookId`, `wordId`; body `WordStateSyncRequest` | `ApiResponse<Unit>` |
| `DELETE` | `me/wordbooks/{bookId}/word-states` | 删除某词书全部单词学习状态 | 是 | path `bookId` | `ApiResponse<Unit>` |
| `POST` | `me/favorites` | 添加收藏 | 是 | `FavoriteSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/favorites` | 分页获取收藏 | 是 | query `page`, `count` | `ApiResponse<PageData<FavoriteDto>>` |
| `DELETE` | `me/favorites/{wordId}` | 移除收藏 | 是 | path `wordId` | `ApiResponse<Unit>` |
| `PUT` | `me/wordbooks/{bookId}/progress` | 新增或更新词书进度 | 是 | path `bookId`; body `WordBookProgressSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/wordbooks/progress` | 获取全部词书进度 | 是 | 无 | `ApiResponse<List<WordBookProgressDto>>` |

### 4.3 词书更新

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `GET` | `me/wordbooks/{bookId}/updates/pending` | 查询指定词书待更新版本 | 是 | path `bookId` | `ApiResponse<PendingWordBookUpdateDto?>` |
| `POST` | `me/wordbooks/{bookId}/updates/{version}/ignore` | 忽略指定词书版本更新 | 是 | path `bookId`, `version` | `ApiResponse<Unit>` |
| `GET` | `me/wordbooks/{bookId}/updates/{version}/manifest` | 获取指定词书更新清单 | 是 | path `bookId`, `version` | `ApiResponse<WordBookUpdateManifestDto>` |
| `GET` | `me/wordbooks/{bookId}/updates/{version}/words` | 分页获取指定词书更新单词 | 是 | path `bookId`, `version`; query `page`, `count` | `ApiResponse<PageData<WordDto>>` |
| `POST` | `me/wordbooks/{bookId}/updates/{version}/complete` | 上报指定词书更新完成 | 是 | path `bookId`, `version` | `ApiResponse<Unit>` |
| `GET` | `me/wordbooks/current/update-candidate` | 查询当前词书更新候选 | 是 | query `trigger` | `ApiResponse<WordBookUpdateCandidateDto?>` |
| `POST` | `me/wordbooks/current/update-actions` | 上报当前词书更新动作 | 是 | `WordBookUpdateActionRequest` | `ApiResponse<Unit>` |
| `GET` | `me/wordbooks/current/updates/{version}/manifest` | 获取当前词书更新清单 | 是 | path `version` | `ApiResponse<WordBookUpdateManifestDto>` |
| `GET` | `me/wordbooks/current/updates/{version}/words` | 分页获取当前词书更新单词 | 是 | path `version`; query `page`, `count` | `ApiResponse<PageData<WordDto>>` |
| `POST` | `me/wordbooks/current/updates/{version}/complete` | 上报当前词书更新完成 | 是 | path `version` | `ApiResponse<Unit>` |

### 4.4 学习记录、时长与打卡

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `POST` | `me/study-records` | 追加学习记录 | 是 | `StudyRecordSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/study-records` | 分页获取学习记录 | 是 | query `page`, `count` | `ApiResponse<PageData<StudyRecordDto>>` |
| `GET` | `me/study-duration` | 分页获取每日学习时长 | 是 | query `page`, `count` | `ApiResponse<PageData<DailyStudyDurationDto>>` |
| `PUT` | `me/study-duration/{date}` | 新增或更新某日学习时长 | 是 | path `date`; body `DailyStudyDurationSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/checkin/config` | 获取打卡配置 | 是 | 无 | `ApiResponse<CheckInConfigDto?>` |
| `PUT` | `me/checkin/config` | 更新打卡配置 | 是 | `CheckInConfigDto` | `ApiResponse<Unit>` |
| `GET` | `me/checkin/status` | 获取打卡状态 | 是 | 无 | `ApiResponse<CheckInStatusDto?>` |
| `GET` | `me/checkin/records` | 分页获取打卡记录 | 是 | query `page`, `count` | `ApiResponse<PageData<CheckInRecordDto>>` |
| `PUT` | `me/checkin/records/{date}` | 新增或更新某日打卡记录 | 是 | path `date`; body `CheckInRecordSyncRequest` | `ApiResponse<Unit>` |

主要 DTO：

| DTO | 字段 |
|---|---|
| `StudyPlanDto` | `dailyNewWords`, `dailyReviewWords`, `testMode`, `wordOrderType` |
| `OnboardingStateDto` | `phase`, `selectedWordBookId?`, `revision`, `updatedAt`, `completedAt?` |
| `AddMyWordBookRequest` | `bookId` |
| `WordBookSelectionSyncRequest` | `selected` |
| `FavoriteSyncRequest` / `FavoriteDto` | `wordId`, `word`, `definitions`, `phonetic?`, `addedDate` |
| `WordStateSyncRequest` | `totalLearnCount`, `lastLearnTime`, `nextReviewTime`, `masteryLevel`, `userStatus`, `repetition`, `interval`, `efactor` |
| `WordBookProgressSyncRequest` | `bookName`, `learnedCount`, `masteredCount`, `totalCount`, `correctCount`, `wrongCount`, `studyDayCount`, `lastStudyDate` |
| `WordBookProgressDto` | `bookId`, `bookName`, `learnedCount`, `masteredCount`, `totalCount`, `correctCount`, `wrongCount`, `studyDayCount`, `lastStudyDate` |
| `WordBookUpdateSummaryDto` | `addedCount`, `modifiedCount`, `removedCount`, `sampleWords` |
| `PendingWordBookUpdateDto` | `bookId`, `bookName`, `currentVersion`, `targetVersion`, `publishedAt`, `summary`, `applyMode`, `changeSummaryText`, `versionScope`, `detailAvailable` |
| `WordBookUpdateManifestDto` | `bookId`, `targetVersion`, `applyMode`, `removedWordIds`, `upsertWordCount`, `pageSize`, `changeSummaryText`, `versionScope`, `detailAvailable` |
| `WordBookUpdateCandidateDto` | `bookId`, `bookName`, `currentVersion`, `targetVersion`, `publishedAt`, `summary`, `applyMode`, `importance`, `detailAvailable`, `estimatedDownloadBytes`, `forcePrompt`, `silentAllowed`, `changeSummaryText`, `versionScope` |
| `WordBookUpdateActionRequest` | `action`, `bookId?`, `targetVersion?`, `trigger?`, `executionMode?`, `deferredUntil?`, `failureReason?` |
| `StudyRecordSyncRequest` / `StudyRecordDto` | `date`, `wordId`, `word`, `definition`, `isNewWord` |
| `DailyStudyDurationSyncRequest` | `totalDurationMs`, `updatedAt`, `isNewPlanCompleted`, `isReviewPlanCompleted` |
| `DailyStudyDurationDto` | `date`, `totalDurationMs`, `updatedAt`, `isNewPlanCompleted`, `isReviewPlanCompleted` |
| `CheckInConfigDto` | `dayBoundaryOffsetMinutes`, `timezoneId` |
| `CheckInStatusDto` | `continuousCheckInDays`, `lastCheckInDate?`, `makeupCardBalance` |
| `CheckInRecordSyncRequest` | `type`, `signedAt`, `updatedAt` |
| `CheckInRecordDto` | `date`, `type`, `signedAt`, `updatedAt` |

## 5. 练习与悬浮同步

源码：`data-sync/.../remoteapi/api/learningsync/LearningSyncApiService.kt`

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `PUT` | `me/practice/settings` | 更新练习设置 | 是 | `PracticeSettingsSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/practice/settings` | 获取练习设置 | 是 | 无 | `ApiResponse<PracticeSettingsDto?>` |
| `PUT` | `me/practice/durations/{date}` | 新增或更新某日练习时长 | 是 | path `date`; body `PracticeDurationSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/practice/durations` | 分页获取练习时长 | 是 | query `page`, `count` | `ApiResponse<PageData<PracticeDurationDto>>` |
| `POST` | `me/practice/sessions` | 追加练习会话 | 是 | `PracticeSessionSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/practice/sessions` | 分页获取练习会话 | 是 | query `page`, `count` | `ApiResponse<PageData<PracticeSessionDto>>` |
| `PUT` | `me/floating/settings` | 更新悬浮复习设置 | 是 | `FloatingSettingsSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/floating/settings` | 获取悬浮复习设置 | 是 | 无 | `ApiResponse<FloatingSettingsDto?>` |
| `PUT` | `me/floating/display-records/{date}` | 新增或更新某日悬浮展示记录 | 是 | path `date`; body `FloatingDisplayRecordSyncRequest` | `ApiResponse<Unit>` |
| `GET` | `me/floating/display-records` | 分页获取悬浮展示记录 | 是 | query `page`, `count` | `ApiResponse<PageData<FloatingDisplayRecordDto>>` |

主要 DTO：

| DTO | 字段 |
|---|---|
| `PracticeSettingsSyncRequest` / `PracticeSettingsDto` | `selectedBookId`, `intervalSeconds`, `loopEnabled`, `showPhonetic`, `showMeaning`, `playbackMode`, `playTimes`, `provider` |
| `PracticeDurationSyncRequest` | `totalDurationMs`, `updatedAt` |
| `PracticeDurationDto` | `date`, `totalDurationMs`, `updatedAt` |
| `PracticeSessionSyncRequest` | `date`, `mode`, `entryType`, `entryCount`, `durationMs`, `createdAt`, `wordIds`, `questionCount`, `completedCount`, `correctCount`, `submitCount` |
| `PracticeSessionDto` | `id`, `date`, `mode`, `entryType`, `entryCount`, `durationMs`, `createdAt`, `wordIds`, `questionCount`, `completedCount`, `correctCount`, `submitCount` |
| `FloatingSettingsSyncRequest` / `FloatingSettingsDto` | `enabled`, `sourceType`, `orderType`, `fieldConfigs`, `selectedWordIds`, `floatingBallX`, `floatingBallY`, `autoStartOnBoot`, `autoStartOnAppLaunch`, `ballOpacityPercent`, `cardOpacityPercent`, `dockConfig?`, `dockState?` |
| `FloatingFieldConfigDto` | `type`, `enabled`, `fontSizeSp` |
| `FloatingDockConfigDto` | `snapTriggerDistanceDp`, `halfHiddenEnabled`, `allowedEdges`, `edgePriority`, `snapAnimationDurationMs`, `tapExpandsCardAfterUnsnap`, `initialDockEdge` |
| `FloatingDockStateDto` | `dockedEdge?`, `crossAxisPercent` |
| `FloatingDisplayRecordSyncRequest` | `displayCount`, `wordIds`, `updatedAt` |
| `FloatingDisplayRecordDto` | `date`, `displayCount`, `wordIds`, `updatedAt` |

## 6. 语音、跟读与真题

语音接口源码：`data-practice/.../remoteapi/api/practice/PracticeApiService.kt`，`speech/.../remoteapi/api/practice/PracticeApiService.kt` 当前同名复用。

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `POST` | `practice/tts` | 合成 TTS 音频 | 是 | `TtsRequestDto` | `ApiResponse<TtsResponseDto>` |
| `POST` | `practice/shadowing/evaluate` | 跟读评测 | 是 | `ShadowingEvaluateRequestDto` | `ApiResponse<ShadowingEvaluateResponseDto>` |
| `GET` | `practice/providers` | 获取语音 Provider 列表 | 是 | 无 | `ApiResponse<ProviderListDto>` |
| `GET` | `speech/providers/aliyun/token` | 获取阿里云语音 token | 是 | 无 | `ApiResponse<AliyunTokenDto>` |

真题接口源码：`data-practice/.../remoteapi/api/practice/ExamPracticeApiService.kt`

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `GET` | `practice/exam/words/{wordId}` | 获取某单词真题练习 | 是 | path `wordId` | `ApiResponse<ExamPracticeWordResponseDto>` |
| `PUT` | `practice/exam/items/{itemId}/favorite` | 更新真题收藏状态 | 是 | path `itemId`; body `ExamItemFavoriteRequest` | `ApiResponse<ExamItemStateDto>` |
| `POST` | `practice/exam/sessions` | 提交真题练习会话 | 是 | `ExamPracticeSessionSubmitRequest` | `ApiResponse<Unit>` |

主要 DTO：

| DTO | 字段 |
|---|---|
| `TtsRequestDto` | `text`, `language`, `voice`, `provider` |
| `TtsResponseDto` | `audioUrl?`, `audioBase64?`, `cacheKey?` |
| `ShadowingEvaluateRequestDto` | `word`, `provider`, `audioBase64` |
| `ShadowingEvaluateResponseDto` | `totalScore`, `pronunciationScore`, `fluencyScore`, `recognizedText`, `intonationScore?`, `stressScore?`, `speedScore?`, `guidanceText?`, `analysisSource?`, `detailSourceNote?`, `audioIssues?` |
| `ShadowingAudioIssueDto` | `type?`, `severity?`, `message?` |
| `ProviderListDto` | `providers` |
| `AliyunTokenDto` | `token`, `expireAt` |
| `ExamPracticeWordResponseDto` | `wordId`, `word`, `examItemDtos`, `totalCount`, `favoriteCount`, `wrongCount`, `objectiveCount` |
| `WordExamItemDto` | `id`, `wordId`, `questionType`, `examCategory`, `paperName`, `difficultyLevel`, `sortOrder`, `groupKey?`, `contentText`, `contextText?`, `options`, `answers`, `leftItems`, `rightItems`, `answerIndexes`, `analysisText?`, `state?` |
| `ExamItemStateDto` | `examItemId`, `favorite`, `wrongBook`, `attemptCount`, `correctCount`, `lastResult?`, `lastAnsweredAt?` |
| `ExamItemFavoriteRequest` | `favorite` |
| `ExamPracticeSessionItemAnswerDto` | `itemId`, `answers`, `answerIndexes`, `viewedAnswer`, `submitCount` |
| `ExamPracticeSessionSubmitRequest` | `wordId`, `sessionId?`, `durationMs`, `questionCount`, `completedCount`, `correctCount`, `submitCount`, `createdAt`, `items` |

## 7. 反馈

源码：`data-feedback/.../remoteapi/api/feedback/FeedbackApiService.kt`

| 方法 | Path | 用途 | 鉴权 | 请求 | 响应 |
|---|---|---|---|---|---|
| `POST` | `me/feedback` | 提交用户反馈和图片 | 是 | multipart `content`, `contact?`, `images[]` | `ApiResponse<Unit>` |

提交约定：

- `content` 与 `contact` 使用 `text/plain`。
- 图片 part 名称固定为 `images`。
- 图片文件来自 `FeedbackImageUploadRequest { bytes, fileName, mimeType }`。
- 空文件名会回退为 `feedback_序号.jpg`。

## 8. 端点覆盖清单

| 领域 | Service | 端点数 |
|---|---|---:|
| 账号认证与资料 | `AuthApiService` | 11 |
| 词书与查词 | `WordBookApiService` | 3 |
| 用户学习同步 | `UserDataSyncApiService` | 34 |
| 练习与悬浮同步 | `LearningSyncApiService` | 10 |
| 语音与跟读 | `PracticeApiService` | 3 |
| 真题练习 | `ExamPracticeApiService` | 3 |
| 语音基础设施 | `SpeechInfraApiService` | 1 |
| 反馈 | `FeedbackApiService` | 1 |

合计：66 个 Retrofit 端点；重复源码包按同一契约合并统计。

package com.chen.memorizewords.data.account.mapper

import com.chen.memorizewords.data.account.remoteapi.dto.CheckInStatusDto
import com.chen.memorizewords.data.account.remoteapi.dto.DailyStudyDurationDto
import com.chen.memorizewords.data.account.remoteapi.dto.LoginBootstrapDto
import com.chen.memorizewords.data.account.remoteapi.dto.LoginResponseDto
import com.chen.memorizewords.data.account.remoteapi.dto.StudyPlanDto
import com.chen.memorizewords.data.account.remoteapi.dto.StudyRecordDto
import com.chen.memorizewords.data.account.remoteapi.dto.TodayStudyStatsDto
import com.chen.memorizewords.data.account.remoteapi.dto.WordBookDto
import com.chen.memorizewords.data.account.remoteapi.dto.WordBookProgressDto
import com.chen.memorizewords.domain.account.model.AccountSession
import com.chen.memorizewords.domain.account.model.AuthLoginResult
import com.chen.memorizewords.domain.sync.model.LoginBootstrap
import com.chen.memorizewords.domain.sync.model.LoginBootstrapCheckInStatus
import com.chen.memorizewords.domain.sync.model.LoginBootstrapDailyStudyDuration
import com.chen.memorizewords.domain.sync.model.LoginBootstrapStudyRecord
import com.chen.memorizewords.domain.sync.model.LoginBootstrapTodayStats
import com.chen.memorizewords.domain.wordbook.model.WordBook
import com.chen.memorizewords.domain.wordbook.model.WordBookContentPackage
import com.chen.memorizewords.domain.wordbook.model.learning.LearningTestMode
import com.chen.memorizewords.domain.wordbook.model.study.StudyPlan
import com.chen.memorizewords.domain.wordbook.model.study.progress.wordbook.WordBookProgress
import com.chen.memorizewords.domain.wordbook.repository.WordOrderType

fun LoginResponseDto.toDomain(nowEpochMillis: Long): AuthLoginResult {
    val bootstrapSnapshot = bootstrap?.toDomain()
    val onboardingSnapshot = bootstrapSnapshot?.onboarding ?: onboarding?.toDomain()
    return AuthLoginResult(
        user = user.toDomain(onboardingSnapshot),
        session = AccountSession(
            accessToken = token,
            refreshToken = refreshToken,
            expiresAtEpochMillis = nowEpochMillis + expiresIn * 1000L
        ),
        onboardingSnapshot = onboardingSnapshot,
        bootstrap = bootstrapSnapshot
    )
}

private fun LoginBootstrapDto.toDomain(): LoginBootstrap? {
    if (version != 1) return null
    val domainBook = currentWordBook?.toDomain(currentWordBookContentVersion)
    return LoginBootstrap(
        version = version,
        serverTime = serverTime,
        businessDate = businessDate,
        onboarding = onboarding?.toDomain(),
        studyPlan = studyPlan?.toDomain(),
        currentWordBook = domainBook,
        currentWordBookProgress = currentWordBookProgress?.toDomain(),
        todayStats = todayStats?.toDomain(),
        todayStudyRecords = todayStudyRecords.map { it.toDomain() },
        todayStudyDuration = todayStudyDuration?.toDomain(),
        checkInStatus = checkInStatus?.toDomain(),
        currentWordBookContentVersion = currentWordBookContentVersion
    )
}

private fun StudyPlanDto.toDomain(): StudyPlan {
    return StudyPlan(
        dailyNewCount = dailyNewWords,
        dailyReviewCount = dailyReviewWords,
        testMode = runCatching { LearningTestMode.valueOf(testMode) }
            .getOrDefault(LearningTestMode.MEANING_CHOICE),
        wordOrderType = runCatching { WordOrderType.valueOf(wordOrderType) }
            .getOrDefault(WordOrderType.RANDOM)
    )
}

private fun WordBookDto.toDomain(contentVersionOverride: Long?): WordBook {
    return WordBook(
        id = id,
        title = title,
        category = category,
        imgUrl = imgUrl,
        description = description,
        totalWords = totalWords,
        contentVersion = contentVersionOverride ?: contentVersion,
        contentPackage = contentPackage?.let { dto ->
            WordBookContentPackage(
                url = dto.url,
                sha256 = dto.sha256,
                sizeBytes = dto.sizeBytes,
                contentType = dto.contentType,
                schemaVersion = dto.schemaVersion,
                contentVersion = dto.contentVersion
            )
        },
        isNew = isNew,
        isHot = isHot,
        isSelected = isSelected,
        isPublic = isPublic,
        createdByUserId = createdByUserId
    )
}

private fun WordBookProgressDto.toDomain(): WordBookProgress {
    return WordBookProgress(
        wordBookId = bookId,
        wordBookName = bookName,
        learningCount = learnedCount,
        masteredCount = masteredCount,
        totalCount = totalCount,
        correctCount = correctCount,
        wrongCount = wrongCount,
        studyDayCount = studyDayCount,
        lastStudyDate = lastStudyDate.orEmpty(),
        revision = revision
    )
}

private fun TodayStudyStatsDto.toDomain(): LoginBootstrapTodayStats {
    return LoginBootstrapTodayStats(
        date = date,
        newWordCount = newWordCount,
        reviewWordCount = reviewWordCount,
        studyDurationMs = studyDurationMs,
        totalStudyDayCount = totalStudyDayCount,
        continuousCheckInDays = continuousCheckInDays
    )
}

private fun StudyRecordDto.toDomain(): LoginBootstrapStudyRecord {
    return LoginBootstrapStudyRecord(
        date = date,
        wordId = wordId,
        word = word,
        definition = definition,
        isNewWord = isNewWord
    )
}

private fun DailyStudyDurationDto.toDomain(): LoginBootstrapDailyStudyDuration {
    return LoginBootstrapDailyStudyDuration(
        date = date,
        totalDurationMs = totalDurationMs,
        updatedAt = updatedAt,
        isNewPlanCompleted = isNewPlanCompleted,
        isReviewPlanCompleted = isReviewPlanCompleted
    )
}

private fun CheckInStatusDto.toDomain(): LoginBootstrapCheckInStatus {
    return LoginBootstrapCheckInStatus(
        continuousCheckInDays = continuousCheckInDays,
        lastCheckInDate = lastCheckInDate,
        makeupCardBalance = makeupCardBalance
    )
}

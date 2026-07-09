package com.chen.memorizewords.data.study.repository.local

import androidx.room.withTransaction
import com.chen.memorizewords.core.common.calendar.CheckInBusinessCalendar
import com.chen.memorizewords.data.study.local.StudyDatabase
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordDao
import com.chen.memorizewords.data.study.local.room.model.study.checkin.CheckInRecordEntity
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationDao
import com.chen.memorizewords.data.study.local.room.model.study.daily.DailyStudyDurationEntity
import com.chen.memorizewords.data.study.local.room.model.study.outbox.StudyPendingOutboxDao
import com.chen.memorizewords.data.study.local.room.model.study.outbox.StudyPendingOutboxEntity
import com.chen.memorizewords.domain.sync.CheckInRecordSyncPayload
import com.chen.memorizewords.domain.sync.DailyStudyDurationSyncPayload
import com.chen.memorizewords.domain.sync.OutboxCommand
import com.chen.memorizewords.domain.sync.OutboxTopic
import com.chen.memorizewords.domain.sync.SyncOperation
import com.google.gson.Gson
import javax.inject.Inject

class StudyRecordLocalStore @Inject constructor(
    private val studyDatabase: StudyDatabase,
    private val dailyStudyDurationDao: DailyStudyDurationDao,
    private val checkInRecordDao: CheckInRecordDao,
    private val studyPendingOutboxDao: StudyPendingOutboxDao,
    private val checkInBusinessCalendar: CheckInBusinessCalendar,
    private val gson: Gson
) {
    suspend fun addStudyDuration(durationMs: Long): LocalWriteResult {
        if (durationMs <= 0L) return LocalWriteResult()
        val date = checkInBusinessCalendar.currentBusinessDate()
        val commands = studyDatabase.withTransaction {
            val updatedAtMs = System.currentTimeMillis()
            dailyStudyDurationDao.addDuration(
                date = date,
                durationMs = durationMs,
                updatedAtMs = updatedAtMs
            )
            dailyStudyDurationDao.getByDate(date)
                ?.let { listOf(it.toOutboxCommand(gson)) }
                .orEmpty()
                .also { pendingCommands ->
                    persistPendingOutboxCommands(pendingCommands)
                }
        }
        return LocalWriteResult(commands)
    }

    suspend fun upsertCheckInRecord(entity: CheckInRecordEntity): LocalWriteResult {
        val commands = listOf(
            OutboxCommand(
                topic = OutboxTopic.CHECKIN_RECORD,
                key = buildCheckInRecordBizKey(entity.date),
                operation = SyncOperation.UPSERT,
                payload = gson.toJson(entity.toSyncPayload())
            )
        )
        studyDatabase.withTransaction {
            checkInRecordDao.upsert(entity)
            persistPendingOutboxCommands(commands)
        }
        return LocalWriteResult(commands)
    }

    suspend fun getPendingOutboxCommands(): List<OutboxCommand> {
        return studyPendingOutboxDao.getAll().map { it.toOutboxCommand() }
    }

    suspend fun deletePendingOutboxCommands(commands: List<OutboxCommand>) {
        commands.map { it.key }
            .distinct()
            .chunked(SQL_BIND_CHUNK_SIZE)
            .forEach { keys -> studyPendingOutboxDao.deleteByBizKeys(keys) }
    }

    private suspend fun persistPendingOutboxCommands(commands: List<OutboxCommand>) {
        if (commands.isNotEmpty()) {
            studyPendingOutboxDao.upsertAll(commands.map { it.toPendingOutboxEntity() })
        }
    }
}

internal fun DailyStudyDurationEntity.toOutboxCommand(gson: Gson): OutboxCommand {
    return OutboxCommand(
        topic = OutboxTopic.DAILY_STUDY_DURATION,
        key = buildDailyStudyDurationBizKey(date),
        operation = SyncOperation.UPSERT,
        payload = gson.toJson(
            DailyStudyDurationSyncPayload(
                date = date,
                totalDurationMs = totalDurationMs,
                updatedAtMs = updatedAtMs,
                isNewPlanCompleted = isNewPlanCompleted,
                isReviewPlanCompleted = isReviewPlanCompleted
            )
        )
    )
}

internal fun buildDailyStudyDurationBizKey(date: String): String {
    return "daily_study_duration:$date"
}

internal fun buildCheckInRecordBizKey(date: String): String {
    return "checkin_record:$date"
}

internal fun CheckInRecordEntity.toSyncPayload(): CheckInRecordSyncPayload {
    return CheckInRecordSyncPayload(
        date = date,
        type = type.name,
        signedAtMs = signedAtMs,
        updatedAtMs = updatedAtMs
    )
}

private fun OutboxCommand.toPendingOutboxEntity(): StudyPendingOutboxEntity {
    return StudyPendingOutboxEntity(
        topic = topic,
        bizKey = key,
        operation = operation.name,
        payload = payload,
        updatedAtMs = updatedAtEpochMillis
    )
}

private fun StudyPendingOutboxEntity.toOutboxCommand(): OutboxCommand {
    return OutboxCommand(
        topic = topic,
        key = bizKey,
        operation = SyncOperation.valueOf(operation),
        payload = payload,
        updatedAtEpochMillis = updatedAtMs
    )
}

private const val SQL_BIND_CHUNK_SIZE = 500

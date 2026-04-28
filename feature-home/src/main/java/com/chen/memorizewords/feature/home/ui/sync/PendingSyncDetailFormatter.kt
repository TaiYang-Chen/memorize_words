package com.chen.memorizewords.feature.home.ui.sync

import com.chen.memorizewords.domain.model.sync.SyncPendingRecord
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class PendingSyncDetailFormatter @Inject constructor(
    private val gson: Gson
) {

    private val prettyGson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun formatTitle(count: Int): String = "$count \u6761\u5f85\u4e0a\u4f20\u6570\u636e"

    fun toItemUi(record: SyncPendingRecord, isExpanded: Boolean): PendingSyncItemUi {
        val payloadParseResult = parsePayload(record.bizType, record.payload)
        val detailFieldsText = payloadParseResult.fields.joinToString("\n") { field ->
            "${field.label}: ${field.value}"
        }
        return PendingSyncItemUi(
            id = record.id,
            bizTypeLabel = formatBizType(record.bizType),
            stateLabel = formatState(record.state),
            operationLabel = formatOperation(record.operation),
            bizKeyText = "bizKey: ${record.bizKey}",
            updatedAtText = "\u66f4\u65b0\u65f6\u95f4: ${formatTimestamp(record.updatedAt)}",
            retryText = record.retryCount.takeIf { it > 0 }
                ?.let { "\u91cd\u8bd5\u6b21\u6570: $it" }
                .orEmpty(),
            failureText = record.failureKind?.takeIf { it.isNotBlank() }
                ?.let { "\u5931\u8d25\u7c7b\u578b: ${formatFailureKind(it)}" }
                .orEmpty(),
            lastErrorText = record.lastError?.takeIf { it.isNotBlank() }
                ?.let { "\u9519\u8bef\u4fe1\u606f: $it" }
                .orEmpty(),
            nextRetryAtText = record.nextRetryAt.takeIf { it > 0L }
                ?.let { "\u4e0b\u6b21\u91cd\u8bd5: ${formatTimestamp(it)}" }
                .orEmpty(),
            expandHintText = if (isExpanded) {
                "\u6536\u8d77\u8be6\u60c5"
            } else {
                "\u5c55\u5f00\u8be6\u60c5"
            },
            detailHintText = payloadParseResult.message,
            detailFields = payloadParseResult.fields,
            detailFieldsText = detailFieldsText,
            rawPayloadText = prettyPrintJson(record.payload),
            isExpanded = isExpanded
        )
    }

    fun parsePayload(bizType: String, payload: String): PayloadParseResult {
        val rootObject = runCatching {
            JsonParser.parseString(payload).takeIf(JsonElement::isJsonObject)?.asJsonObject
        }.getOrNull() ?: return PayloadParseResult(
            fields = emptyList(),
            message = PARSE_FAILED_MESSAGE
        )

        val fields = when (bizType) {
            STUDY_RECORD -> buildFields(
                rootObject,
                label("\u4e1a\u52a1\u65e5\u671f", "date"),
                label("\u5355\u8bcd ID", "wordId"),
                label("\u5355\u8bcd", "word"),
                label("\u91ca\u4e49", "definition"),
                label("\u662f\u5426\u65b0\u8bcd", "isNewWord")
            )

            DAILY_STUDY_DURATION -> buildFields(
                rootObject,
                label("\u4e1a\u52a1\u65e5\u671f", "date"),
                label("\u5b66\u4e60\u65f6\u957f", "totalDurationMs"),
                label("\u66f4\u65b0\u65f6\u95f4", "updatedAt"),
                label("\u65b0\u8bcd\u8ba1\u5212\u5b8c\u6210", "isNewPlanCompleted"),
                label("\u590d\u4e60\u8ba1\u5212\u5b8c\u6210", "isReviewPlanCompleted")
            )

            PRACTICE_DURATION -> buildFields(
                rootObject,
                label("\u4e1a\u52a1\u65e5\u671f", "date"),
                label("\u7ec3\u4e60\u65f6\u957f", "totalDurationMs"),
                label("\u66f4\u65b0\u65f6\u95f4", "updatedAt")
            )

            PRACTICE_SESSION -> buildFields(
                rootObject,
                label("\u8bb0\u5f55 ID", "id"),
                label("\u4e1a\u52a1\u65e5\u671f", "date"),
                label("\u6a21\u5f0f", "mode"),
                label("\u8fdb\u5165\u65b9\u5f0f", "entryType"),
                label("\u8fdb\u5165\u6570\u91cf", "entryCount"),
                label("\u7ec3\u4e60\u65f6\u957f", "durationMs"),
                label("\u521b\u5efa\u65f6\u95f4", "createdAt"),
                label("\u5355\u8bcd ID \u5217\u8868", "wordIds"),
                label("\u9898\u76ee\u6570", "questionCount"),
                label("\u5b8c\u6210\u6570", "completedCount"),
                label("\u6b63\u786e\u6570", "correctCount"),
                label("\u63d0\u4ea4\u6570", "submitCount")
            )

            FAVORITE -> buildFields(
                rootObject,
                label("\u5355\u8bcd ID", "wordId"),
                label("\u5355\u8bcd", "word"),
                label("\u91ca\u4e49", "definitions"),
                label("\u97f3\u6807", "phonetic"),
                label("\u6536\u85cf\u65e5\u671f", "addedDate"),
                label("\u6536\u85cf\u65f6\u95f4", "addedAt")
            )

            WORD_BOOK_PROGRESS -> buildFields(
                rootObject,
                label("\u8bcd\u4e66 ID", "bookId"),
                label("\u8bcd\u4e66\u540d", "bookName"),
                label("\u5b66\u4e60\u4e2d\u6570\u91cf", "learnedCount"),
                label("\u5df2\u638c\u63e1\u6570\u91cf", "masteredCount"),
                label("\u603b\u8bcd\u6570", "totalCount"),
                label("\u6b63\u786e\u6570", "correctCount"),
                label("\u9519\u8bef\u6570", "wrongCount"),
                label("\u5b66\u4e60\u5929\u6570", "studyDayCount"),
                label("\u6700\u8fd1\u5b66\u4e60\u65e5\u671f", "lastStudyDate")
            )

            WORD_STATE_UPSERT -> buildFields(
                rootObject,
                label("\u8bcd\u4e66 ID", "bookId"),
                label("\u5355\u8bcd ID", "wordId"),
                label("\u5b66\u4e60\u6b21\u6570", "totalLearnCount"),
                label("\u4e0a\u6b21\u5b66\u4e60\u65f6\u95f4", "lastLearnTime"),
                label("\u4e0b\u6b21\u590d\u4e60\u65f6\u95f4", "nextReviewTime"),
                label("\u638c\u63e1\u7b49\u7ea7", "masteryLevel"),
                label("\u7528\u6237\u72b6\u6001", "userStatus"),
                label("\u91cd\u590d\u6b21\u6570", "repetition"),
                label("\u590d\u4e60\u95f4\u9694", "interval"),
                label("\u9057\u5fd8\u56e0\u5b50", "efactor")
            )

            WORD_STATE_DELETE_BY_BOOK -> buildFields(rootObject, label("\u8bcd\u4e66 ID", "bookId"))
            WORD_BOOK_SELECTION -> buildFields(rootObject, label("\u8bcd\u4e66 ID", "bookId"))

            STUDY_PLAN -> buildFields(
                rootObject,
                label("\u6bcf\u65e5\u65b0\u8bcd\u6570", "dailyNewWords"),
                label("\u6bcf\u65e5\u590d\u4e60\u6570", "dailyReviewWords"),
                label("\u6d4b\u8bd5\u6a21\u5f0f", "testMode"),
                label("\u5355\u8bcd\u987a\u5e8f", "wordOrderType")
            )

            ONBOARDING_STATE -> buildFields(
                rootObject,
                label("\u5f15\u5bfc\u9636\u6bb5", "phase"),
                label("\u5df2\u9009\u8bcd\u4e66 ID", "selectedWordBookId"),
                label("\u7248\u672c\u53f7", "revision"),
                label("\u66f4\u65b0\u65f6\u95f4", "updatedAt"),
                label("\u5b8c\u6210\u65f6\u95f4", "completedAt")
            )

            PRACTICE_SETTINGS -> buildFields(
                rootObject,
                label("\u8bcd\u4e66 ID", "selectedBookId"),
                label("\u95f4\u9694\u79d2\u6570", "intervalSeconds"),
                label("\u5faa\u73af\u64ad\u653e", "loopEnabled"),
                label("\u663e\u793a\u97f3\u6807", "showPhonetic"),
                label("\u663e\u793a\u91ca\u4e49", "showMeaning"),
                label("\u64ad\u653e\u6a21\u5f0f", "playbackMode"),
                label("\u64ad\u653e\u6b21\u6570", "playTimes"),
                label("\u8bed\u97f3\u63d0\u4f9b\u65b9", "provider")
            )

            FLOATING_SETTINGS -> buildFields(
                rootObject,
                label("\u542f\u7528\u72b6\u6001", "enabled"),
                label("\u6765\u6e90\u7c7b\u578b", "sourceType"),
                label("\u6392\u5e8f\u65b9\u5f0f", "orderType"),
                label("\u5b57\u6bb5\u914d\u7f6e", "fieldConfigsJson"),
                label("\u5df2\u9009\u5355\u8bcd ID", "selectedWordIdsJson"),
                label("\u60ac\u6d6e\u7403 X", "floatingBallX"),
                label("\u60ac\u6d6e\u7403 Y", "floatingBallY"),
                label("\u5f00\u673a\u542f\u52a8", "autoStartOnBoot"),
                label("\u5e94\u7528\u542f\u52a8\u65f6\u5f00\u542f", "autoStartOnAppLaunch"),
                label("\u60ac\u6d6e\u7403\u900f\u660e\u5ea6", "ballOpacityPercent"),
                label("\u5361\u7247\u900f\u660e\u5ea6", "cardOpacityPercent"),
                label("\u505c\u9760\u914d\u7f6e", "dockConfigJson"),
                label("\u505c\u9760\u72b6\u6001", "dockStateJson")
            )

            FLOATING_DISPLAY_RECORD -> buildFields(
                rootObject,
                label("\u4e1a\u52a1\u65e5\u671f", "date"),
                label("\u5c55\u793a\u6b21\u6570", "displayCount"),
                label("\u5355\u8bcd ID \u5217\u8868", "wordIds"),
                label("\u66f4\u65b0\u65f6\u95f4", "updatedAt")
            )

            CHECKIN_RECORD -> buildFields(
                rootObject,
                label("\u4e1a\u52a1\u65e5\u671f", "date"),
                label("\u7b7e\u5230\u7c7b\u578b", "type"),
                label("\u7b7e\u5230\u65f6\u95f4", "signedAt"),
                label("\u66f4\u65b0\u65f6\u95f4", "updatedAt")
            )

            else -> extractAllFields(rootObject)
        }

        return PayloadParseResult(
            fields = fields,
            message = if (fields.isEmpty()) PARSE_FAILED_MESSAGE else ""
        )
    }

    private fun label(label: String, key: String): Pair<String, String> = label to key

    private fun buildFields(
        rootObject: JsonObject,
        vararg fieldMappings: Pair<String, String>
    ): List<PendingSyncDetailFieldUi> {
        return fieldMappings.mapNotNull { (label, key) ->
            rootObject.get(key)?.takeUnless(JsonElement::isJsonNull)?.let { element ->
                PendingSyncDetailFieldUi(label = label, value = formatValue(key, element))
            }
        }
    }

    private fun extractAllFields(rootObject: JsonObject): List<PendingSyncDetailFieldUi> {
        return rootObject.entrySet().mapNotNull { (key, value) ->
            value.takeUnless(JsonElement::isJsonNull)?.let {
                PendingSyncDetailFieldUi(label = key, value = formatValue(key, it))
            }
        }
    }

    private fun formatValue(key: String, element: JsonElement): String {
        return when {
            element.isJsonNull -> ""
            element.isJsonPrimitive -> {
                val primitive = element.asJsonPrimitive
                when {
                    primitive.isBoolean -> if (primitive.asBoolean) "\u662f" else "\u5426"
                    primitive.isNumber -> formatNumericValue(key, primitive.asString)
                    else -> primitive.asString
                }
            }

            element.isJsonArray -> formatArray(key, element.asJsonArray)
            else -> gson.toJson(element)
        }
    }

    private fun formatNumericValue(key: String, rawValue: String): String {
        val longValue = rawValue.toLongOrNull()
        return when {
            (key.endsWith("At") || key.endsWith("Time")) && longValue != null -> {
                formatTimestamp(longValue)
            }

            (key.endsWith("DurationMs") || key == "interval") && longValue != null -> {
                formatDuration(longValue)
            }

            else -> rawValue
        }
    }

    private fun formatArray(key: String, array: JsonArray): String {
        return when (key) {
            "wordIds" -> array.joinToString(", ") { element -> element.asString }
            else -> gson.toJson(array)
        }
    }

    private fun formatBizType(bizType: String): String {
        return when (bizType) {
            STUDY_RECORD -> "\u5b66\u4e60\u8bb0\u5f55"
            DAILY_STUDY_DURATION -> "\u5b66\u4e60\u65f6\u957f"
            PRACTICE_DURATION -> "\u7ec3\u4e60\u65f6\u957f"
            PRACTICE_SESSION -> "\u7ec3\u4e60\u8bb0\u5f55"
            FAVORITE -> "\u6536\u85cf\u5355\u8bcd"
            WORD_BOOK_PROGRESS -> "\u8bcd\u4e66\u8fdb\u5ea6"
            WORD_STATE_UPSERT -> "\u5355\u8bcd\u72b6\u6001\u66f4\u65b0"
            WORD_STATE_DELETE_BY_BOOK -> "\u6309\u8bcd\u4e66\u5220\u9664\u5355\u8bcd\u72b6\u6001"
            WORD_BOOK_SELECTION -> "\u5f53\u524d\u8bcd\u4e66"
            STUDY_PLAN -> "\u5b66\u4e60\u8ba1\u5212"
            ONBOARDING_STATE -> "\u5f15\u5bfc\u72b6\u6001"
            PRACTICE_SETTINGS -> "\u7ec3\u4e60\u8bbe\u7f6e"
            FLOATING_SETTINGS -> "\u60ac\u6d6e\u590d\u4e60\u8bbe\u7f6e"
            FLOATING_DISPLAY_RECORD -> "\u60ac\u6d6e\u590d\u4e60\u5c55\u793a\u8bb0\u5f55"
            CHECKIN_RECORD -> "\u7b7e\u5230\u8bb0\u5f55"
            else -> bizType
        }
    }

    private fun formatState(state: String): String {
        return when (state) {
            "QUEUED" -> "\u6392\u961f\u4e2d"
            "IN_FLIGHT" -> "\u4e0a\u4f20\u4e2d"
            "RETRY_WAITING" -> "\u7b49\u5f85\u91cd\u8bd5"
            "BLOCKED" -> "\u5df2\u963b\u585e"
            else -> state
        }
    }

    private fun formatOperation(operation: String): String {
        return when (operation) {
            "UPSERT" -> "\u65b0\u589e\u6216\u66f4\u65b0"
            "DELETE" -> "\u5220\u9664"
            else -> operation
        }
    }

    private fun formatFailureKind(failureKind: String): String {
        return when (failureKind) {
            "NETWORK" -> "\u7f51\u7edc"
            "AUTH" -> "\u9274\u6743"
            "SERVER" -> "\u670d\u52a1\u7aef"
            "RATE_LIMIT" -> "\u9650\u6d41"
            "CLIENT" -> "\u5ba2\u6237\u7aef"
            "UNKNOWN" -> "\u672a\u77e5"
            else -> failureKind
        }
    }

    private fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) {
            return "0 ms"
        }
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return buildList {
            if (hours > 0L) add("${hours}\u5c0f\u65f6")
            if (minutes > 0L) add("${minutes}\u5206\u949f")
            if (seconds > 0L) add("${seconds}\u79d2")
            if (isEmpty()) add("${durationMs} ms")
        }.joinToString("")
    }

    private fun formatTimestamp(timestamp: Long): String {
        if (timestamp <= 0L) {
            return "-"
        }
        return dateTimeFormat.format(Date(timestamp))
    }

    private fun prettyPrintJson(payload: String): String {
        val element = runCatching { JsonParser.parseString(payload) }.getOrNull() ?: return payload
        return prettyGson.toJson(element)
    }

    data class PayloadParseResult(
        val fields: List<PendingSyncDetailFieldUi>,
        val message: String
    )

    companion object {
        private const val PARSE_FAILED_MESSAGE =
            "\u5b57\u6bb5\u89e3\u6790\u5931\u8d25\uff0c\u4ec5\u5c55\u793a\u539f\u59cb\u5185\u5bb9"

        private const val STUDY_RECORD = "STUDY_RECORD"
        private const val DAILY_STUDY_DURATION = "DAILY_STUDY_DURATION"
        private const val PRACTICE_DURATION = "PRACTICE_DURATION"
        private const val PRACTICE_SESSION = "PRACTICE_SESSION"
        private const val FAVORITE = "FAVORITE"
        private const val WORD_BOOK_PROGRESS = "WORD_BOOK_PROGRESS"
        private const val WORD_STATE_UPSERT = "WORD_STATE_UPSERT"
        private const val WORD_STATE_DELETE_BY_BOOK = "WORD_STATE_DELETE_BY_BOOK"
        private const val WORD_BOOK_SELECTION = "WORD_BOOK_SELECTION"
        private const val STUDY_PLAN = "STUDY_PLAN"
        private const val ONBOARDING_STATE = "ONBOARDING_STATE"
        private const val PRACTICE_SETTINGS = "PRACTICE_SETTINGS"
        private const val FLOATING_SETTINGS = "FLOATING_SETTINGS"
        private const val FLOATING_DISPLAY_RECORD = "FLOATING_DISPLAY_RECORD"
        private const val CHECKIN_RECORD = "CHECKIN_RECORD"
    }
}

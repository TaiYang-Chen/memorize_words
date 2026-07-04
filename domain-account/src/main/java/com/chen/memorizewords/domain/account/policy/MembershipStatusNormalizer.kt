package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil

private val isoDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")
private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

fun normalizeMembershipStatus(
    status: MembershipStatus?,
    currentTimeMillis: Long = currentMembershipTimeMillis()
): MembershipStatus? {
    status ?: return null
    val validUntilAt = status.validUntilAt ?: status.validUntilDate
        ?.takeIf(::isValidIsoDate)
        ?.let(::legacyValidUntilAt)
    val active = validUntilAt != null && validUntilAt > currentTimeMillis
    return status.copy(
        active = active,
        validUntilAt = validUntilAt,
        remainingDays = if (active) {
            calculateRemainingDays(currentTimeMillis, validUntilAt)
        } else {
            0
        }
    )
}

fun currentMembershipTimeMillis(): Long = System.currentTimeMillis()

fun currentLocalMembershipDate(): String = localIsoDateFormat().format(Date())

private fun calculateRemainingDays(currentTimeMillis: Long, validUntilAt: Long): Int {
    val remainingMillis = validUntilAt - currentTimeMillis
    if (remainingMillis <= 0L) return 0
    return ceil(remainingMillis.toDouble() / MILLIS_PER_DAY).toInt().coerceAtLeast(1)
}

private fun legacyValidUntilAt(value: String): Long? {
    val parsedDate = strictLocalIsoDateFormat().parse(value) ?: return null
    return Calendar.getInstance().apply {
        time = parsedDate
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun isValidIsoDate(value: String): Boolean {
    if (!isoDateRegex.matches(value)) return false
    return try {
        strictIsoDateFormat().parse(value)
        true
    } catch (_: ParseException) {
        false
    }
}

private fun strictIsoDateFormat(): SimpleDateFormat {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private fun strictLocalIsoDateFormat(): SimpleDateFormat {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
}

private fun localIsoDateFormat(): SimpleDateFormat {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
}

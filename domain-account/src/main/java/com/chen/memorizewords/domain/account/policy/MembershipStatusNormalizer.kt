package com.chen.memorizewords.domain.account.policy

import com.chen.memorizewords.domain.account.model.membership.MembershipStatus
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private val isoDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

fun normalizeMembershipStatus(
    status: MembershipStatus?,
    currentDate: String = currentLocalMembershipDate()
): MembershipStatus? {
    status ?: return null
    val validUntilDate = status.validUntilDate?.takeIf(::isValidIsoDate)
    val normalizedCurrentDate = currentDate.takeIf(::isValidIsoDate) ?: currentLocalMembershipDate()
    val active = validUntilDate != null && validUntilDate >= normalizedCurrentDate
    return status.copy(
        active = active,
        remainingDays = if (active) {
            calculateInclusiveRemainingDays(normalizedCurrentDate, validUntilDate)
        } else {
            0
        }
    )
}

fun currentLocalMembershipDate(): String = localIsoDateFormat().format(Date())

private fun calculateInclusiveRemainingDays(startDate: String, endDate: String): Int {
    val formatter = strictIsoDateFormat()
    val start = formatter.parse(startDate)?.time ?: return 0
    val end = formatter.parse(endDate)?.time ?: return 0
    val diffDays = TimeUnit.MILLISECONDS.toDays(end - start)
    return (diffDays + 1).coerceAtLeast(0).toInt()
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

private fun localIsoDateFormat(): SimpleDateFormat {
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        isLenient = false
    }
}

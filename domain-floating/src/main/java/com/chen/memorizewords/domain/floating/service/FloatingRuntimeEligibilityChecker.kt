package com.chen.memorizewords.domain.floating.service

import com.chen.memorizewords.domain.floating.model.FloatingRuntimeEligibility

fun interface FloatingRuntimeEligibilityChecker {
    suspend fun checkEligibility(): FloatingRuntimeEligibility
}

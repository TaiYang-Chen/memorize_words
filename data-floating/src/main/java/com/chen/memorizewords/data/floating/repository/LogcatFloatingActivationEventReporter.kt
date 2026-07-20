package com.chen.memorizewords.data.floating.repository

import android.util.Log
import com.chen.memorizewords.domain.floating.service.FloatingActivationEvent
import com.chen.memorizewords.domain.floating.service.FloatingActivationEventReporter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogcatFloatingActivationEventReporter @Inject constructor() :
    FloatingActivationEventReporter {
    override fun report(event: FloatingActivationEvent, attributes: Map<String, String>) {
        val payload = attributes.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { "${it.key}=${it.value}" }
        Log.i(TAG, if (payload.isBlank()) event.name else "${event.name}:$payload")
    }

    private companion object {
        const val TAG = "FloatingActivation"
    }
}

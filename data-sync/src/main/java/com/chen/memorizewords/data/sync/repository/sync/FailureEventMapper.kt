package com.chen.memorizewords.data.sync.repository.sync

import android.util.Log
import com.chen.memorizewords.core.network.http.FailureEventDeliveryMode
import com.chen.memorizewords.core.network.http.FailureQueuedEvent
import com.chen.memorizewords.core.network.http.SyncEventParam
import com.chen.memorizewords.core.network.http.SyncEventParams
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncDeliveryMode
import com.chen.memorizewords.data.sync.local.room.model.sync.FailedSyncState
import com.squareup.moshi.Moshi
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.Invocation

@Singleton
class FailureEventMapper @Inject constructor(
    private val moshi: Moshi
) {
    private val metadataCache = ConcurrentHashMap<Method, FailureMetadata>()
    private val anyAdapter by lazy { moshi.adapter(Any::class.java) }

    fun map(invocation: Invocation): FailedSyncEvent? {
        val method = invocation.method()
        val annotation = method.getAnnotation(FailureQueuedEvent::class.java) ?: return null
        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        return try {
            val metadata = metadataCache[method] ?: parseMetadata(method)?.also {
                metadataCache[method] = it
            } ?: return null
            val params = extractParams(invocation)
            val paramsJson = anyAdapter.toJson(params)
            require(paramsJson.toByteArray(Charsets.UTF_8).size <= FailedSyncEventStore.MAX_PARAMS_BYTES) {
                "failed sync params exceed ${FailedSyncEventStore.MAX_PARAMS_BYTES} bytes"
            }
            val sequence = metadata.sequenceParam.takeIf(String::isNotBlank)?.let { name ->
                (params[name] as? Number)?.toLong()
                    ?: throw IllegalArgumentException("sequence parameter is missing or not numeric: $name")
            }
            FailedSyncEvent(
                eventId = eventId,
                eventType = metadata.eventType,
                schemaVersion = metadata.schemaVersion,
                deliveryMode = metadata.deliveryMode,
                dedupeKey = metadata.dedupeTemplate.takeIf(String::isNotBlank)?.let { expand(it, params) },
                orderingKey = expand(metadata.orderingTemplate, params),
                sequence = sequence,
                paramsJson = paramsJson,
                occurredAtMs = now
            )
        } catch (failure: Exception) {
            Log.e(
                TAG,
                "failure_queue mapping_failed method=${method.declaringClass.name}.${method.name}",
                failure
            )
            FailedSyncEvent(
                eventId = eventId,
                eventType = annotation.eventType.ifBlank { "INVALID_EVENT" },
                schemaVersion = annotation.schemaVersion,
                deliveryMode = when (annotation.deliveryMode) {
                    FailureEventDeliveryMode.APPEND -> FailedSyncDeliveryMode.APPEND
                    FailureEventDeliveryMode.LATEST -> FailedSyncDeliveryMode.LATEST
                },
                dedupeKey = null,
                orderingKey = "blocked:${method.declaringClass.name}.${method.name}:$eventId",
                sequence = null,
                paramsJson = "{}",
                occurredAtMs = now,
                initialState = FailedSyncState.BLOCKED,
                initialError = "event mapping failed: ${failure.message.orEmpty()}"
            )
        }
    }

    private fun parseMetadata(method: Method): FailureMetadata? {
        val annotation = method.getAnnotation(FailureQueuedEvent::class.java) ?: return null
        require(annotation.eventType.isNotBlank()) { "eventType is blank: $method" }
        require(annotation.orderingKey.isNotBlank()) { "orderingKey is blank: $method" }
        if (annotation.deliveryMode == FailureEventDeliveryMode.LATEST) {
            require(annotation.dedupeKey.isNotBlank()) { "LATEST requires dedupeKey: $method" }
        }
        return FailureMetadata(
            eventType = annotation.eventType,
            schemaVersion = annotation.schemaVersion,
            deliveryMode = when (annotation.deliveryMode) {
                FailureEventDeliveryMode.APPEND -> FailedSyncDeliveryMode.APPEND
                FailureEventDeliveryMode.LATEST -> FailedSyncDeliveryMode.LATEST
            },
            dedupeTemplate = annotation.dedupeKey,
            orderingTemplate = annotation.orderingKey,
            sequenceParam = annotation.sequenceParam
        )
    }

    private fun extractParams(invocation: Invocation): LinkedHashMap<String, Any?> {
        val method = invocation.method()
        val params = linkedMapOf<String, Any?>()
        method.parameterAnnotations.forEachIndexed { index, annotations ->
            val argument = invocation.arguments().getOrNull(index)
            if (annotations.any { it is SyncEventParams } && argument != null) {
                val json = moshi.adapter(argument.javaClass).toJson(argument)
                val decoded = anyAdapter.fromJson(json)
                if (decoded is Map<*, *>) {
                    decoded.forEach { (key, value) -> key?.toString()?.let { params[it] = value } }
                } else {
                    params["body"] = decoded
                }
            }
            annotations.filterIsInstance<SyncEventParam>().firstOrNull()?.let { parameter ->
                params[parameter.value] = argument
            }
        }
        return params
    }

    private fun expand(template: String, params: Map<String, Any?>): String {
        return TEMPLATE_REGEX.replace(template) { match ->
            val name = match.groupValues[1]
            params[name]?.let(::syncTemplateValue)
                ?: throw IllegalArgumentException("missing sync template parameter: $name")
        }
    }

    private data class FailureMetadata(
        val eventType: String,
        val schemaVersion: Int,
        val deliveryMode: FailedSyncDeliveryMode,
        val dedupeTemplate: String,
        val orderingTemplate: String,
        val sequenceParam: String
    )

    private companion object {
        const val TAG = "FailureEventMapper"

        // Escape both braces explicitly. Android's ICU regex engine treats an
        // unmatched closing brace as a syntax error, even though the host JVM
        // accepts it as a literal.
        val TEMPLATE_REGEX = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    }
}

internal fun syncTemplateValue(value: Any): String = when (value) {
    is Byte, is Short, is Int, is Long -> (value as Number).toLong().toString()
    is Float, is Double -> {
        val number = (value as Number).toDouble()
        val integral = number.toLong()
        if (number.isFinite() && number == integral.toDouble()) integral.toString()
        else java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
    }
    else -> value.toString()
}

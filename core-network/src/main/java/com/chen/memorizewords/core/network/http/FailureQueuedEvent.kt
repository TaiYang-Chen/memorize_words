package com.chen.memorizewords.core.network.http

enum class FailureEventDeliveryMode {
    APPEND,
    LATEST
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class FailureQueuedEvent(
    val eventType: String,
    val schemaVersion: Int = 1,
    val deliveryMode: FailureEventDeliveryMode,
    val dedupeKey: String = "",
    val orderingKey: String,
    val sequenceParam: String = ""
)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SyncEventParams

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SyncEventParam(val value: String)

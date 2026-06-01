package com.chen.memorizewords.domain.practice
interface PracticeEffectHandler {
    suspend fun handle(effect: PracticeEffect)
}

class CompositePracticeEffectHandler(
    private val handlers: List<PracticeEffectHandler>
) : PracticeEffectHandler {
    override suspend fun handle(effect: PracticeEffect) {
        handlers.forEach { handler ->
            handler.handle(effect)
        }
    }
}

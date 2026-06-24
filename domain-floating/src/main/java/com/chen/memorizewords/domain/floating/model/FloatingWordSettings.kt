package com.chen.memorizewords.domain.floating.model
data class FloatingWordSettings(
    val enabled: Boolean = false,
    val autoStartOnBoot: Boolean = false,
    val autoStartOnAppLaunch: Boolean = false,
    val ballSizePercent: Int = 100,
    val ballOpacityPercent: Int = 100,
    val cardOpacityPercent: Int = 100,
    val cardGapDp: Int = 40,
    val sourceType: FloatingWordSourceType = FloatingWordSourceType.CURRENT_BOOK,
    val orderType: FloatingWordOrderType = FloatingWordOrderType.RANDOM,
    val fieldConfigs: List<FloatingWordFieldConfig> = defaultFieldConfigs(),
    val selectedWordIds: List<Long> = emptyList(),
    val floatingBallX: Int = 0,
    val floatingBallY: Int = 0,
    val dockConfig: FloatingDockConfig = FloatingDockConfig(),
    val dockState: FloatingDockState? = null
) {
    companion object {
        fun defaultFieldConfigs(): List<FloatingWordFieldConfig> {
            return listOf(
                FloatingWordFieldConfig(FloatingWordFieldType.WORD, true, 18),
                FloatingWordFieldConfig(FloatingWordFieldType.PHONETIC, true, 11),
                FloatingWordFieldConfig(FloatingWordFieldType.MEANING, true, 12),
                FloatingWordFieldConfig(FloatingWordFieldType.PART_OF_SPEECH, false, 10),
                FloatingWordFieldConfig(FloatingWordFieldType.EXAMPLE, false, 10),
                FloatingWordFieldConfig(FloatingWordFieldType.NOTE, false, 10),
                FloatingWordFieldConfig(FloatingWordFieldType.IMAGE, false, 100)
            )
        }
    }
}

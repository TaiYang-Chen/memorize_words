package com.chen.memorizewords.startup

class LocalAssetResetPolicy(
    private val resetVersion: Int = RESET_VERSION,
    val resettableDatabaseNames: List<String> = listOf(
        "memorize_words.db",
        "memorize_words_arch_v1.db",
        "memorize_words_arch_v1_sync.db",
        "memorize_words_arch_v1_account.db",
        "memorize_words_arch_v1_word.db",
        "memorize_words_arch_v1_wordbook.db",
        "memorize_words_arch_v1_study.db",
        "memorize_words_arch_v1_practice.db",
        "memorize_words_arch_v1_sync_outbox.db",
        "memorize_words_arch_v1_floating.db"
    ),
    val preservedStringKeys: List<String> = listOf(
        "key_access_token",
        "key_refresh_token"
    ),
    val preservedLongKeys: List<String> = listOf(
        "key_expires_at",
        "user_id",
        "local_user_data_owner_user_id"
    ),
    val preservedBooleanKeys: List<String> = emptyList()
) {
    fun shouldReset(appliedVersion: Int): Boolean = appliedVersion < resetVersion

    companion object {
        const val RESET_VERSION = 2
    }
}

package com.chen.memorizewords.core.database

data class DatabaseConfig(
    val name: String,
    val destructiveUpgrade: Boolean
)

interface DatabaseTransactionRunner {
    suspend fun <T> transaction(block: suspend () -> T): T
}

object NewArchitectureDatabase {
    const val NAME = "memorize_words_arch_v1.db"
    val config = DatabaseConfig(name = NAME, destructiveUpgrade = true)

    fun contextName(contextName: String): String {
        val normalized = contextName
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        require(normalized.isNotBlank()) { "contextName must contain at least one safe character" }
        return "memorize_words_arch_v1_$normalized.db"
    }
}

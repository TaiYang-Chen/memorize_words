package com.chen.memorizewords.feature.feedback.ui.agreement

object AgreementTypes {
    const val PRIVACY = "privacy"
    const val TERMS = "terms"

    fun normalize(value: String?): String {
        return when (value) {
            TERMS -> TERMS
            else -> PRIVACY
        }
    }
}

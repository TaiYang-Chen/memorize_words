plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.speech"
    defaultConfig {
        val wordTtsProvider = (project.findProperty("WORD_TTS_PROVIDER") as String?) ?: "ALIYUN"
        val sentenceTtsProvider = (project.findProperty("SENTENCE_TTS_PROVIDER") as String?) ?: "BAIDU"
        val evaluationProvider = (project.findProperty("EVALUATION_PROVIDER") as String?) ?: "XUNFEI"

        buildConfigField("String", "WORD_TTS_PROVIDER", "\"$wordTtsProvider\"")
        buildConfigField("String", "SENTENCE_TTS_PROVIDER", "\"$sentenceTtsProvider\"")
        buildConfigField("String", "EVALUATION_PROVIDER", "\"$evaluationProvider\"")
    }

    resourcePrefix("lib_speech_")
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":speech-api"))
    implementation(project(":domain"))
    implementation(project(":core-network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    ksp(libs.hilt.compiler)
}

plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.speech"
    defaultConfig {
        val wordTtsProvider = (project.findProperty("WORD_TTS_PROVIDER") as String?) ?: "BAIDU"
        val sentenceTtsProvider = (project.findProperty("SENTENCE_TTS_PROVIDER") as String?) ?: "BAIDU"
        val evaluationProvider = (project.findProperty("EVALUATION_PROVIDER") as String?) ?: "BAIDU"
        val baiduAppId = (project.findProperty("BAIDU_APP_ID") as String?) ?: ""
        val baiduApiKey = (project.findProperty("BAIDU_API_KEY") as String?) ?: ""
        val baiduSecretKey = (project.findProperty("BAIDU_SECRET_KEY") as String?) ?: ""
        val aliyunAppKey = (project.findProperty("ALIYUN_APP_KEY") as String?) ?: ""
        val aliyunWordVoice = (project.findProperty("ALIYUN_WORD_VOICE") as String?) ?: "ava"
        val aliyunSentenceVoice = (project.findProperty("ALIYUN_SENTENCE_VOICE") as String?) ?: "xiaoyun"

        buildConfigField("String", "WORD_TTS_PROVIDER", "\"$wordTtsProvider\"")
        buildConfigField("String", "SENTENCE_TTS_PROVIDER", "\"$sentenceTtsProvider\"")
        buildConfigField("String", "EVALUATION_PROVIDER", "\"$evaluationProvider\"")
        buildConfigField("String", "BAIDU_APP_ID", "\"$baiduAppId\"")
        buildConfigField("String", "BAIDU_API_KEY", "\"$baiduApiKey\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"$baiduSecretKey\"")
        buildConfigField("String", "ALIYUN_APP_KEY", "\"$aliyunAppKey\"")
        buildConfigField("String", "ALIYUN_WORD_VOICE", "\"$aliyunWordVoice\"")
        buildConfigField("String", "ALIYUN_SENTENCE_VOICE", "\"$aliyunSentenceVoice\"")
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
    implementation(project(":domain-practice"))
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

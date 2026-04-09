plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.chen.memorizewords.speech"
    compileSdk = 36

    defaultConfig {
        minSdk = 25

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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":speech-api"))
    implementation(project(":network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.compiler)
}

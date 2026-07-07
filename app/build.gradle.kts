import java.util.Properties

plugins {
    id("memorize.android-application")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { stream -> load(stream) }
    }
}

fun releaseProperty(name: String): String? {
    return (localProperties.getProperty(name) ?: System.getenv(name))
        ?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.chen.memorizewords"
    defaultConfig {
        applicationId = "com.chen.memorizewords"
        versionCode = 1
        versionName = "1.0"

        val wxAppId = (project.findProperty("WX_APP_ID") as String?) ?: ""
        val wxUniversalLink = (project.findProperty("WX_UNIVERSAL_LINK") as String?) ?: ""
        val qqAppId = (project.findProperty("QQ_APP_ID") as String?) ?: ""

        buildConfigField("String", "WX_APP_ID", "\"$wxAppId\"")
        buildConfigField("String", "WX_UNIVERSAL_LINK", "\"$wxUniversalLink\"")
        buildConfigField("String", "QQ_APP_ID", "\"$qqAppId\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = releaseProperty("RELEASE_STORE_FILE")
            val storePasswordValue = releaseProperty("RELEASE_STORE_PASSWORD")
            val keyAliasValue = releaseProperty("RELEASE_KEY_ALIAS")
            val keyPasswordValue = releaseProperty("RELEASE_KEY_PASSWORD")

            if (
                storeFilePath != null &&
                storePasswordValue != null &&
                keyAliasValue != null &&
                keyPasswordValue != null
            ) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures{
        dataBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-navigation"))
    implementation(project(":data"))
    implementation(project(":domain"))
    implementation(project(":speech"))
    implementation(project(":feature-feedback"))
    implementation(project(":feature-floating-review"))
    implementation(project(":feature-home"))
    implementation(project(":feature-learning"))
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-user"))
    implementation(project(":feature-wordbook"))

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation(libs.tencent.mmkv.static)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.wechat.sdk.android)

    debugImplementation(libs.debug.db)
    testImplementation(kotlin("test"))
}

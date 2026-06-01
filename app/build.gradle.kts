plugins {
    id("memorize.android-application")
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

    buildTypes {
        release {
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
    implementation(project(":data-account"))
    implementation(project(":data-feedback"))
    implementation(project(":data-floating"))
    implementation(project(":data-practice"))
    implementation(project(":data-study"))
    implementation(project(":data-sync"))
    implementation(project(":data-wordbook"))
    implementation(project(":domain-account"))
    implementation(project(":domain-floating"))
    implementation(project(":domain-practice"))
    implementation(project(":domain-sync"))
    implementation(project(":domain-wordbook"))
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
}

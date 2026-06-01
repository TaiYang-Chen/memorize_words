plugins {
    id("memorize.android-hilt-library")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.chen.memorizewords.feature.user"
    defaultConfig {
        val wxAppId = (project.findProperty("WX_APP_ID") as String?) ?: ""
        val wxUniversalLink = (project.findProperty("WX_UNIVERSAL_LINK") as String?) ?: ""
        val qqAppId = (project.findProperty("QQ_APP_ID") as String?) ?: ""

        buildConfigField("String", "WX_APP_ID", "\"$wxAppId\"")
        buildConfigField("String", "WX_UNIVERSAL_LINK", "\"$wxUniversalLink\"")
        buildConfigField("String", "QQ_APP_ID", "\"$qqAppId\"")
    }

    resourcePrefix("feature_user_")
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures{
        dataBinding = true
        buildConfig = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-navigation"))
    implementation(project(":domain-account"))

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.coil)
    implementation(libs.material)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.qq.open.sdk)
    implementation(libs.wechat.sdk.android)
    implementation(libs.wheel.picker)
    implementation(libs.yalantis.ucrop)
}

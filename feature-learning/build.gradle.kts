plugins {
    id("memorize.android-hilt-library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.navigation.safeargs)
    id("kotlin-kapt")
}

val enablePaparazzi = providers.gradleProperty("enablePaparazzi")
    .map { it.toBoolean() }
    .orElse(false)

if (enablePaparazzi.get()) {
    apply(plugin = "app.cash.paparazzi")
}

android {
    namespace = "com.chen.memorizewords.feature.learning"
    resourcePrefix("feature_learning_")
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures{
        dataBinding = true
        compose = true
        viewBinding = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    if (enablePaparazzi.get()) {
        sourceSets.getByName("test").java.srcDir("src/paparazzi/java")
    }
}

// 添加以下配置来消除 Hilt/KSP 参数未识别的警告
kapt {
    correctErrorTypes = true
    arguments {
        arg("dagger.hilt.internal.useAggregatingRootProcessor", "false")
        arg("dagger.hilt.android.internal.projectType", "LIBRARY")
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-navigation"))
    implementation(project(":domain"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.gridlayout)
    implementation(libs.material)
    implementation(libs.coil)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.ui.tooling)

}

if (enablePaparazzi.get()) {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("verifyPaparazziDebug")
    }
}

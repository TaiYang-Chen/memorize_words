plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data"
    resourcePrefix("data_")

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val apiBaseUrl = providers.gradleProperty("memorize.apiBaseUrl")
            .orElse("https://example.invalid/api/")
            .get()
        val enableNetworkBodyLogging = providers.gradleProperty("memorize.enableNetworkBodyLogging")
            .orElse("false")
            .get()
            .toBooleanStrictOrNull() ?: false

        buildConfigField("String", "API_BASE_URL", apiBaseUrl.toBuildConfigString())
        buildConfigField("boolean", "ENABLE_NETWORK_BODY_LOGGING", enableNetworkBodyLogging.toString())
    }

    sourceSets {
        getByName("main") {
            java.srcDirs(
                "../data-account/src/main/java",
                "../data-feedback/src/main/java",
                "../data-floating/src/main/java",
                "../data-practice/src/main/java",
                "../data-study/src/main/java",
                "../data-sync/src/main/java",
                "../data-wordbook/src/main/java"
            )
        }
        getByName("test") {
            java.srcDirs(
                "../data-account/src/test/java",
                "../data-feedback/src/test/java",
                "../data-floating/src/test/java",
                "../data-practice/src/test/java",
                "../data-study/src/test/java",
                "../data-sync/src/test/java",
                "../data-wordbook/src/test/java"
            )
        }
        getByName("androidTest") {
            java.srcDirs(
                "../data-account/src/androidTest/java",
                "../data-feedback/src/androidTest/java",
                "../data-floating/src/androidTest/java",
                "../data-practice/src/androidTest/java",
                "../data-study/src/androidTest/java",
                "../data-sync/src/androidTest/java",
                "../data-wordbook/src/androidTest/java"
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":core-common"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.tencent.mmkv.static)
    implementation(libs.gson)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    implementation(libs.hilt.android)

    testImplementation(kotlin("test"))

    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
}

fun String.toBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

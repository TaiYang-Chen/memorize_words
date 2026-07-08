import java.net.Inet4Address
import java.net.NetworkInterface

plugins {
    id("memorize.android-hilt-library")
}

val releaseApiBaseUrl = "https://47.95.233.62:8080/api/"

android {
    namespace = "com.chen.memorizewords.data"
    resourcePrefix("data_")

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val enableNetworkBodyLogging = providers.gradleProperty("memorize.enableNetworkBodyLogging")
            .orElse("false")
            .get()
            .toBooleanStrictOrNull() ?: false

        buildConfigField("boolean", "ENABLE_NETWORK_BODY_LOGGING", enableNetworkBodyLogging.toString())
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", debugApiBaseUrl().toBuildConfigString())
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", releaseApiBaseUrl().toBuildConfigString())
        }
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
                "../data-word/src/main/java",
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
                "../data-word/src/test/java",
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
                "../data-word/src/androidTest/java",
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

gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { task ->
        task.name.equals("assembleRelease", ignoreCase = true) ||
            task.name.equals("bundleRelease", ignoreCase = true) ||
            task.name.equals("packageRelease", ignoreCase = true)
    }
    if (releaseRequested) {
        val apiBaseUrl = releaseApiBaseUrl()
        require(apiBaseUrl.startsWith("https://", ignoreCase = true)) {
            "Release network baseUrl must use HTTPS."
        }
        require(apiBaseUrl.endsWith("/")) {
            "Release network baseUrl must end with '/'."
        }
    }
}

fun String.toBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

fun debugApiBaseUrl(): String {
    return explicitApiBaseUrl() ?: autoDetectedDebugApiBaseUrl()
}

fun releaseApiBaseUrl(): String {
    return explicitApiBaseUrl() ?: releaseApiBaseUrl
}

fun explicitApiBaseUrl(): String? {
    return providers.gradleProperty("memorize.apiBaseUrl")
        .orElse(providers.environmentVariable("MEMORIZE_API_BASE_URL"))
        .orNull
        ?.takeIf { it.isNotBlank() }
}

fun autoDetectedDebugApiBaseUrl(): String {
    val hostIp = NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { networkInterface ->
            networkInterface.isUp &&
                !networkInterface.isLoopback &&
                !networkInterface.isVirtual &&
                !networkInterface.displayName.contains("VMware", ignoreCase = true) &&
                !networkInterface.displayName.contains("VirtualBox", ignoreCase = true) &&
                !networkInterface.displayName.contains("vEthernet", ignoreCase = true) &&
                !networkInterface.displayName.contains("Loopback", ignoreCase = true) &&
                !networkInterface.displayName.contains("WSL", ignoreCase = true)
        }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .map { it.hostAddress }
        .firstOrNull { ip ->
            !ip.startsWith("127.") && !ip.startsWith("169.254.")
        }
        ?: "127.0.0.1"
    return "https://$hostIp:8080/api/"
}

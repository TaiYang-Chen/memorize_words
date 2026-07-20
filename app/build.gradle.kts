import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Properties
import org.gradle.api.tasks.Delete

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

val releaseArchiveDir = rootProject.layout.projectDirectory.dir("release/generated")
val releaseArchiveStagingDir = rootProject.layout.projectDirectory.dir("release/.generated-staging")

val cleanReleaseArchive by tasks.registering(Delete::class) {
    group = "build"
    description = "Remove previously generated release archives before a new release build."
    delete(releaseArchiveDir, releaseArchiveStagingDir)
}

tasks.matching { task ->
    task.name == "assembleRelease" || task.name == "bundleRelease"
}.configureEach {
    mustRunAfter(cleanReleaseArchive)
}

val archiveReleaseArtifacts by tasks.registering {
    group = "build"
    description = "Build and archive signed release artifacts without copying signing credentials."
    dependsOn(cleanReleaseArchive, "assembleRelease", "bundleRelease")

    doLast {
        val releaseSigning = android.signingConfigs.getByName("release")
        check(releaseSigning.storeFile?.isFile == true) {
            "Release signing keystore is missing. Check RELEASE_STORE_FILE in local.properties."
        }

        val archiveDir = releaseArchiveDir.asFile
        val stagingDir = releaseArchiveStagingDir.asFile
        check(!archiveDir.exists()) {
            "Release archive directory was not cleaned: ${archiveDir.absolutePath}"
        }
        check(stagingDir.mkdirs() || stagingDir.isDirectory) {
            "Unable to create release archive staging directory: ${stagingDir.absolutePath}"
        }

        val artifacts = listOf(
            layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile to
                stagingDir.resolve("memorize_words-release.apk"),
            layout.buildDirectory.file("outputs/bundle/release/app-release.aab").get().asFile to
                stagingDir.resolve("memorize_words-release.aab"),
            layout.buildDirectory.file("outputs/mapping/release/mapping.txt").get().asFile to
                stagingDir.resolve("mapping.txt")
        )

        artifacts.forEach { (source, destination) ->
            check(source.isFile) { "Expected release artifact is missing: ${source.absolutePath}" }
            source.copyTo(destination, overwrite = true)
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

        val checksumFiles = artifacts.map { (_, destination) -> destination }
        stagingDir.resolve("SHA256SUMS.txt").writeText(
            checksumFiles.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()) {
                file -> "${sha256(file)}  ${file.name}"
            }
        )
        try {
            Files.move(
                stagingDir.toPath(),
                archiveDir.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(stagingDir.toPath(), archiveDir.toPath())
        }
    }
}

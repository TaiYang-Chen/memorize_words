plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    api(project(":core-common"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation(kotlin("test"))
}

kotlin {
    sourceSets {
        named("main") {
            kotlin.srcDirs(
                "../domain-account/src/main/java",
                "../domain-feedback/src/main/java",
                "../domain-floating/src/main/java",
                "../domain-practice/src/main/java",
                "../domain-study/src/main/java",
                "../domain-sync/src/main/java",
                "../domain-word/src/main/java",
                "../domain-wordbook/src/main/java"
            )
        }
        named("test") {
            kotlin.srcDirs(
                "../domain-account/src/test/java",
                "../domain-feedback/src/test/java",
                "../domain-floating/src/test/java",
                "../domain-practice/src/test/java",
                "../domain-study/src/test/java",
                "../domain-sync/src/test/java",
                "../domain-word/src/test/java",
                "../domain-wordbook/src/test/java"
            )
        }
    }
}

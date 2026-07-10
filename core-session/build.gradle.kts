plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.core.session"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

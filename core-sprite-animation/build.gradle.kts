plugins {
    id("memorize.android-library")
}

android {
    namespace = "com.chen.memorizewords.core.sprite"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.feedback"
    resourcePrefix("data_feedback_")
}

dependencies {
    implementation(project(":domain-feedback"))
    implementation(project(":core-network"))
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

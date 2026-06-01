plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.sync"
    resourcePrefix("data_sync_")
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":domain-sync"))
    implementation(project(":domain-account"))
    implementation(project(":domain-feedback"))
    implementation(project(":domain-floating"))
    implementation(project(":domain-practice"))
    implementation(project(":domain-study"))
    implementation(project(":domain-word"))
    implementation(project(":domain-wordbook"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.tencent.mmkv.static)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    implementation(libs.gson)
    implementation(libs.javax.inject)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation("junit:junit:4.13.2")
}

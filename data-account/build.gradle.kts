plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.account"
    resourcePrefix("data_account_")
}

dependencies {
    implementation(project(":domain-account"))
    implementation(project(":domain-floating"))
    implementation(project(":domain-practice"))
    implementation(project(":domain-sync"))
    implementation(project(":domain-wordbook"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.tencent.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.hilt.android)
    implementation(libs.javax.inject)
    ksp(libs.hilt.compiler)
    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.study"
    resourcePrefix("data_study_")
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":domain-account"))
    implementation(project(":domain-sync"))
    implementation(project(":domain-study"))
    implementation(project(":domain-word"))
    implementation(project(":domain-wordbook"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.tencent.mmkv.static)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

}

plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.floating"
    resourcePrefix("data_floating_")
}

dependencies {
    implementation(project(":domain-account"))
    implementation(project(":domain-floating"))
    implementation(project(":domain-sync"))
    implementation(project(":domain-word"))
    implementation(project(":core-common"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.tencent.mmkv.static)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

}

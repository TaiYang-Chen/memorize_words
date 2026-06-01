plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.practice"
    resourcePrefix("data_practice_")
}

dependencies {
    implementation(project(":domain-account"))
    implementation(project(":domain-practice"))
    implementation(project(":domain-sync"))
    implementation(project(":core-common"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit)
    implementation(libs.tencent.mmkv.static)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")
}

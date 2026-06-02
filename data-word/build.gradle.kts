plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.data.word"
    resourcePrefix("data_word_")
}

dependencies {
    implementation(project(":domain-sync"))
    implementation(project(":domain-study"))
    implementation(project(":domain-word"))
    implementation(project(":core-database"))
    implementation(project(":core-network"))
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.gson)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.moshi.kotlin)
    implementation(libs.retrofit)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)

}

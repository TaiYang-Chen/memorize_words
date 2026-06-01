plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    api(libs.logging.interceptor)
    api(libs.retrofit)
    api(libs.retrofit.converter.moshi)
    api(libs.moshi.kotlin)
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    implementation(libs.androidx.paging.common)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}

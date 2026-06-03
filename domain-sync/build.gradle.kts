plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":domain-wordbook"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}

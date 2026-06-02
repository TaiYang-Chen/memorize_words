plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    implementation(project(":domain-study"))
    implementation(project(":domain-word"))
    implementation(project(":domain-wordbook"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}

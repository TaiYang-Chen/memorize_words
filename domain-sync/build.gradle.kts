plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    implementation(project(":core-common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
}

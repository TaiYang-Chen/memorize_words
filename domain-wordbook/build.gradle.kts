plugins {
    id("memorize.kotlin-jvm")
}

dependencies {
    api(project(":core-common"))
    api(project(":domain-word"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.javax.inject)
    testImplementation("junit:junit:4.13.2")
}

plugins {
    id("memorize.android-library")
}

android {
    namespace = "com.chen.memorizewords.core.navigation"

    resourcePrefix("lib_core_navigation_")

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(project(":domain-practice"))
    implementation(project(":domain-wordbook"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)
}

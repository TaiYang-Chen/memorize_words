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
    implementation(libs.androidx.core.ktx)
    implementation(libs.javax.inject)
}

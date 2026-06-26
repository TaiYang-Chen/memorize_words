plugins {
    id("memorize.android-library")
}

android {
    namespace = "com.chen.memorizewords.core.ui"

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        dataBinding = true
    }
}

dependencies {
    api(project(":core-common"))
    api(project(":core-navigation"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.coil)
}

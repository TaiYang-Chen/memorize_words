plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.feature.feedback"
    resourcePrefix("feature_feedback_")
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures{
        dataBinding = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-navigation"))
    implementation(project(":domain-feedback"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)

    implementation(libs.glide)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}

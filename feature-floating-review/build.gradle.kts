plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.feature.floatingreview"
    resourcePrefix("feature_floating_review_")
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    buildFeatures {
        dataBinding = true
        viewBinding = true
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    implementation(project(":core-ui"))
    implementation(project(":core-navigation"))
    implementation(project(":domain"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.material)
    implementation(libs.coil)
    implementation(libs.hilt.android)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    ksp(libs.hilt.compiler)

    testImplementation(kotlin("test"))

}

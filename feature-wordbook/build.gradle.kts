plugins {
    id("memorize.android-hilt-library")
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.chen.memorizewords.feature.wordbook"
    resourcePrefix("feature_wordbook_")
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
    implementation(project(":domain-study"))
    implementation(project(":domain-word"))
    implementation(project(":domain-wordbook"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.leanback.paging)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.coil)
    implementation(libs.hilt.android)
    implementation(libs.material)
    ksp(libs.hilt.compiler)

}

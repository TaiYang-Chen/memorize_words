plugins {
    id("memorize.android-hilt-library")
}

android {
    namespace = "com.chen.memorizewords.feature.home"
    resourcePrefix("feature_home_")
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
    implementation(project(":domain-account"))
    implementation(project(":domain-floating"))
    implementation(project(":domain-practice"))
    implementation(project(":domain-study"))
    implementation(project(":domain-sync"))
    implementation(project(":domain-word"))
    implementation(project(":domain-wordbook"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.coil)
    implementation(libs.gson)

    implementation(libs.hilt.android)
    implementation(libs.androidx.gridlayout)
    ksp(libs.hilt.compiler)

}

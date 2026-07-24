plugins {
    id("memorize.android-library")
}

android {
    namespace = "com.chen.memorizewords.core.sprite"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.gson)
    implementation(libs.javax.inject)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

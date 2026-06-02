plugins {
    `java-gradle-plugin`
    id("org.jetbrains.kotlin.jvm") version "2.2.0"
}

dependencies {
    compileOnly(gradleKotlinDsl())
    compileOnly("com.android.tools.build:gradle:8.12.3")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.0")
}

gradlePlugin {
    plugins {
        register("androidApplicationConvention") {
            id = "memorize.android-application"
            implementationClass = "MemorizeAndroidApplicationConventionPlugin"
        }
        register("androidLibraryConvention") {
            id = "memorize.android-library"
            implementationClass = "MemorizeAndroidLibraryConventionPlugin"
        }
        register("androidHiltLibraryConvention") {
            id = "memorize.android-hilt-library"
            implementationClass = "MemorizeAndroidHiltLibraryConventionPlugin"
        }
        register("kotlinJvmConvention") {
            id = "memorize.kotlin-jvm"
            implementationClass = "MemorizeKotlinJvmConventionPlugin"
        }
    }
}

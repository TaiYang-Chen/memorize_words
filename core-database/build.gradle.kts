plugins {
    id("memorize.android-library")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.chen.memorizewords.core.database"
    resourcePrefix("core_database_")
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}

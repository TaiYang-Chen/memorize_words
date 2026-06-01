pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
        maven { setUrl("https://artifact.bytedance.com/repository/ttgamesdk/") }
    }
}

rootProject.name = "memorize_words"
include(":app")
include(":core-common")
include(":core-ui")
include(":core-navigation")
include(":core-network")
include(":core-database")
include(":data-account")
include(":data-feedback")
include(":data-floating")
include(":data-practice")
include(":data-study")
include(":data-sync")
include(":data-wordbook")
include(":domain-account")
include(":domain-feedback")
include(":domain-floating")
include(":domain-practice")
include(":domain-study")
include(":domain-sync")
include(":domain-word")
include(":domain-wordbook")
include(":feature-user")
include(":feature-wordbook")
include(":feature-learning")
include(":feature-home")
include(":feature-onboarding")
include(":feature-feedback")
include(":feature-floating-review")
include(":speech-api")
include(":speech")

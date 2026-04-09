pluginManagement {
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
include(":data")
include(":domain")
include(":feature-user")
include(":network")
include(":feature-wordbook")
include(":feature-learning")
include(":feature-home")
include(":feature-feedback")
include(":feature-floating-review")
include(":speech-api")
include(":speech")

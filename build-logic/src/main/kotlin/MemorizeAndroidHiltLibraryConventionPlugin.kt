import org.gradle.api.Plugin
import org.gradle.api.Project

class MemorizeAndroidHiltLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("memorize.android-library")
        target.pluginManager.apply("com.google.dagger.hilt.android")
        target.pluginManager.apply("com.google.devtools.ksp")
    }
}

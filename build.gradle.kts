// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("verifyPresentationLayerBoundaries") {
    group = "verification"
    description = "Fail when presentation modules import data/network classes directly."

    doLast {
        val moduleRoots = rootDir.listFiles()
            ?.filter { file ->
                file.isDirectory && (
                    file.name.startsWith("module_") ||
                        file.name.startsWith("feature-") ||
                        file.name.startsWith("feature_")
                    )
            }
            .orEmpty()
        val forbiddenImports = listOf("import com.chen.memorizewords.data.", "import com.chen.memorizewords.network.")
        val violations = mutableListOf<String>()

        moduleRoots.forEach { moduleRoot ->
            val srcRoot = moduleRoot.resolve("src/main/java")
            if (!srcRoot.exists()) return@forEach

            srcRoot.walkTopDown()
                .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
                .forEach { file ->
                    val lines = file.readLines()
                    lines.forEachIndexed { index, line ->
                        if (forbiddenImports.any { token -> line.trimStart().startsWith(token) }) {
                            val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                            violations += "$relPath:${index + 1}: $line"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found forbidden data/network imports in presentation modules:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyDomainLayerBoundaries") {
    group = "verification"
    description = "Fail when domain layer imports Android, Paging, or serialization-only platform APIs."

    doLast {
        val domainRoot = rootDir.resolve("domain/src/main/java")
        if (!domainRoot.exists()) return@doLast

        val violations = mutableListOf<String>()
        val forbiddenTokens = listOf(
            "import android.",
            "import androidx.paging.",
            "import java.io.Serializable"
        )

        domainRoot.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    if (forbiddenTokens.any { token -> line.trimStart().startsWith(token) }) {
                        val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                        violations += "$relPath:${index + 1}: $line"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found forbidden Android/Paging/platform imports in domain:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyAppEntryBoundaries") {
    group = "verification"
    description = "Fail when app imports feature Activity/Service classes directly."

    doLast {
        val appRoot = rootDir.resolve("app/src/main/java")
        if (!appRoot.exists()) return@doLast

        val violations = mutableListOf<String>()
        val directEntryRegex = Regex(
            """^\s*import\s+com\.chen\.(module_|feature[_-])[\w.]+\.(\w*(Activity|Service))\s*$"""
        )

        appRoot.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    if (directEntryRegex.containsMatchIn(line)) {
                        val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                        violations += "$relPath:${index + 1}: $line"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found direct feature Activity/Service imports in app:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyFeatureApplicationLayerBoundaries") {
    group = "verification"
    description = "Fail when app/feature modules import domain application-layer types."

    doLast {
        val moduleRoots = rootDir.listFiles()
            ?.filter { file ->
                file.isDirectory && (
                    file.name == "app" ||
                        file.name.startsWith("module_") ||
                        file.name.startsWith("feature-") ||
                        file.name.startsWith("feature_")
                    )
            }
            .orEmpty()

        val violations = mutableListOf<String>()
        val forbiddenImport = "import com.chen.memorizewords.domain.application."

        moduleRoots.forEach { moduleRoot ->
            val srcRoot = moduleRoot.resolve("src/main/java")
            if (!srcRoot.exists()) return@forEach

            srcRoot.walkTopDown()
                .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (line.trimStart().startsWith(forbiddenImport)) {
                            val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                            violations += "$relPath:${index + 1}: $line"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found forbidden domain application-layer imports in app/feature modules:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyViewModelFrameworkLeakage") {
    group = "verification"
    description = "Fail when ViewModels depend on Android Context/Application directly."

    doLast {
        val moduleRoots = rootDir.listFiles()
            ?.filter { file ->
                file.isDirectory && (
                    file.name == "app" ||
                        file.name.startsWith("module_") ||
                        file.name.startsWith("feature-") ||
                        file.name.startsWith("feature_") ||
                        file.name.startsWith("core-")
                    )
            }
            .orEmpty()

        val forbiddenTokens = listOf(
            "import android.content.Context",
            "import android.app.Application",
            "@ApplicationContext"
        )
        val violations = mutableListOf<String>()

        moduleRoots.forEach { moduleRoot ->
            val srcRoot = moduleRoot.resolve("src/main/java")
            if (!srcRoot.exists()) return@forEach

            srcRoot.walkTopDown()
                .filter { file ->
                    file.isFile &&
                        (file.extension == "kt" || file.extension == "java") &&
                        file.name.contains("ViewModel")
                }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (forbiddenTokens.any { token -> line.contains(token) }) {
                            val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                            violations += "$relPath:${index + 1}: $line"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found Android Context/Application leakage in ViewModels:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyUiEventContracts") {
    group = "verification"
    description = "Fail when UiEvent dialog contracts include function types."

    doLast {
        val uiEventFile = rootDir.resolve("core-ui/src/main/java/com/chen/base/vm/UiEvent.kt")
        if (!uiEventFile.exists()) return@doLast

        val violations = mutableListOf<String>()
        uiEventFile.readLines().forEachIndexed { index, line ->
            if (line.contains("->")) {
                val relPath = uiEventFile.relativeTo(rootDir).invariantSeparatorsPath
                violations += "$relPath:${index + 1}: $line"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found function types in UiEvent contracts:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyDomainPackageLayoutConsistency") {
    group = "verification"
    description = "Fail when domain source file paths do not match their declared package."

    doLast {
        val domainRoot = rootDir.resolve("domain/src/main/java")
        if (!domainRoot.exists()) return@doLast

        val violations = mutableListOf<String>()
        val packageRegex = Regex("""^\s*package\s+([A-Za-z0-9_.]+)\s*$""")

        domainRoot.walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
            .forEach { file ->
                val declaredPackage = file.useLines { lines ->
                    lines.mapNotNull { line -> packageRegex.matchEntire(line)?.groupValues?.get(1) }
                        .firstOrNull()
                } ?: return@forEach

                val expectedDir = declaredPackage.replace('.', '/')
                val actualDir = file.parentFile.relativeTo(domainRoot).invariantSeparatorsPath
                if (actualDir != expectedDir) {
                    val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                    violations += "$relPath: package=$declaredPackage path=$actualDir"
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found domain files whose paths do not match declared packages:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyPresentationLayerBoundaries"))
        dependsOn(rootProject.tasks.named("verifyDomainLayerBoundaries"))
        dependsOn(rootProject.tasks.named("verifyAppEntryBoundaries"))
        dependsOn(rootProject.tasks.named("verifyFeatureApplicationLayerBoundaries"))
        dependsOn(rootProject.tasks.named("verifyDomainPackageLayoutConsistency"))
        dependsOn(rootProject.tasks.named("verifyViewModelFrameworkLeakage"))
        dependsOn(rootProject.tasks.named("verifyUiEventContracts"))
    }
}

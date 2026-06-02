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
        val forbiddenImports = listOf(
            "import com.chen.memorizewords.data.",
            "import com.chen.memorizewords.network.",
            "import com.chen.memorizewords.core.network."
        )
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
        val domainRoots = rootDir.listFiles()
            ?.filter { file -> file.isDirectory && (file.name == "domain" || file.name.startsWith("domain-")) }
            ?.map { file -> file.resolve("src/main/java") }
            ?.filter { file -> file.exists() }
            .orEmpty()
        if (domainRoots.isEmpty()) return@doLast

        val violations = mutableListOf<String>()
        val forbiddenTokens = listOf(
            "import android.",
            "import androidx.paging.",
            "import androidx.room.",
            "import retrofit2.",
            "import okhttp3.",
            "import com.squareup.moshi.",
            "import java.io.Serializable",
            "import com.chen.memorizewords.data.",
            "import com.chen.memorizewords.network.",
            "import com.chen.memorizewords.core.network."
        )

        domainRoots.forEach { domainRoot ->
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
            """^\s*import\s+com\.chen\.memorizewords\.feature\.[\w.]+\.(\w*(Activity|Service))\s*$"""
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

tasks.register("verifyAppCompositionBoundaries") {
    group = "verification"
    description = "Fail when app aggregates transitional or duplicate implementation modules."

    doLast {
        val appBuildFile = rootDir.resolve("app/build.gradle.kts")
        if (!appBuildFile.exists()) return@doLast

        val forbiddenProjectRefs = listOf(
            "project(\":data-word\")"
        )
        val violations = mutableListOf<String>()

        appBuildFile.readLines().forEachIndexed { index, line ->
            if (forbiddenProjectRefs.any { token -> line.contains(token) }) {
                val relPath = appBuildFile.relativeTo(rootDir).invariantSeparatorsPath
                violations += "$relPath:${index + 1}: $line"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found duplicate/transitional implementation modules aggregated by app:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyFeatureModuleProjectDependencies") {
    group = "verification"
    description = "Fail when feature modules depend on data/network implementation modules."

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

        val forbiddenProjectRefs = listOf(
            "project(\":data",
            "project(\":network\")",
            "project(\":core-network\")"
        )
        val violations = mutableListOf<String>()

        moduleRoots.forEach { moduleRoot ->
            val buildFile = moduleRoot.resolve("build.gradle.kts")
            if (!buildFile.exists()) return@forEach

            buildFile.readLines().forEachIndexed { index, line ->
                if (forbiddenProjectRefs.any { token -> line.contains(token) }) {
                    val relPath = buildFile.relativeTo(rootDir).invariantSeparatorsPath
                    violations += "$relPath:${index + 1}: $line"
                }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found forbidden implementation-module dependencies in feature modules:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyNewArchitectureProjectDependencies") {
    group = "verification"
    description = "Fail when new architecture modules violate project dependency directions."

    doLast {
        val violations = mutableListOf<String>()

        fun scanBuildFile(moduleName: String, forbiddenProjectRefs: List<String>) {
            val buildFile = rootDir.resolve("$moduleName/build.gradle.kts")
            if (!buildFile.exists()) return

            buildFile.readLines().forEachIndexed { index, line ->
                if (forbiddenProjectRefs.any { token -> line.contains(token) }) {
                    val relPath = buildFile.relativeTo(rootDir).invariantSeparatorsPath
                    violations += "$relPath:${index + 1}: $line"
                }
            }
        }

        rootDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name.startsWith("domain-") }
            .orEmpty()
            .forEach { module ->
                scanBuildFile(
                    moduleName = module.name,
                    forbiddenProjectRefs = listOf(
                        "project(\":app\")",
                        "project(\":data",
                        "project(\":network\")",
                        "project(\":core-network\")",
                        "project(\":core-database\")",
                        "project(\":feature-",
                        "project(\":feature_"
                    )
                )
            }

        rootDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name.startsWith("data-") }
            .orEmpty()
            .forEach { module ->
                val otherDataModuleRefs = rootDir.listFiles()
                    ?.filter { file -> file.isDirectory && file.name.startsWith("data-") && file.name != module.name }
                    ?.map { file -> "project(\":${file.name}\")" }
                    .orEmpty()
                scanBuildFile(
                    moduleName = module.name,
                    forbiddenProjectRefs = otherDataModuleRefs + listOf(
                        "project(\":app\")",
                        "project(\":feature-",
                        "project(\":feature_",
                        "project(\":data\")",
                        "project(\":domain\")",
                        "project(\":network\")"
                    )
                )
            }

        scanBuildFile(
            moduleName = "domain-account",
            forbiddenProjectRefs = listOf(
                "project(\":domain-floating\")",
                "project(\":domain-wordbook\")"
            )
        )

        rootDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name.startsWith("core-") }
            .orEmpty()
            .forEach { module ->
                scanBuildFile(
                    moduleName = module.name,
                    forbiddenProjectRefs = listOf(
                        "project(\":app\")",
                        "project(\":data",
                        "project(\":network\")",
                        "project(\":domain",
                        "project(\":feature-",
                        "project(\":feature_"
                    )
                )
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found invalid new-architecture project dependencies:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyDataModuleImportBoundaries") {
    group = "verification"
    description = "Fail when a data module imports another data module's implementation package."

    doLast {
        val dataModules = rootDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name.startsWith("data-") }
            .orEmpty()
        if (dataModules.isEmpty()) return@doLast

        val knownContexts = dataModules.map { module ->
            module.name.removePrefix("data-").replace('-', '.')
        }.toSet()
        val importRegex = Regex("""^\s*import\s+com\.chen\.memorizewords\.data\.([A-Za-z0-9_.]+)""")
        val violations = mutableListOf<String>()

        dataModules.forEach { module ->
            val ownContext = module.name.removePrefix("data-").replace('-', '.')
            val sourceRoots = listOf(
                module.resolve("src/main/java")
            ).filter { file -> file.exists() }

            sourceRoots.forEach { sourceRoot ->
                sourceRoot.walkTopDown()
                    .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
                    .forEach { file ->
                        file.readLines().forEachIndexed { index, line ->
                            val importedContext = importRegex.find(line)
                                ?.groupValues
                                ?.getOrNull(1)
                                ?.substringBefore('.')
                                ?: return@forEachIndexed
                            if (importedContext in knownContexts && importedContext != ownContext) {
                                val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                                violations += "$relPath:${index + 1}: $line"
                            }
                        }
                    }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found cross-data implementation imports:")
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
        val uiEventFile = rootDir.resolve(
            "core-ui/src/main/java/com/chen/memorizewords/core/ui/vm/UiEvent.kt"
        )
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

tasks.register("verifyStartupPathIsolation") {
    group = "verification"
    description = "Fail when app startup path imports legacy data-layer classes."

    doLast {
        val startupFiles = listOf(
            rootDir.resolve("app/src/main/java/com/chen/memorizewords/MemorizeWordsApplication.kt"),
            rootDir.resolve("app/src/main/java/com/chen/memorizewords/SplashActivity.kt")
        ) + rootDir.resolve("app/src/main/java/com/chen/memorizewords/startup")
            .walkTopDown()
            .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
            .toList()

        val violations = mutableListOf<String>()
        val forbiddenImports = listOf(
            "import com.chen.memorizewords.data.",
            "import com.chen.memorizewords.network."
        )

        startupFiles
            .filter { it.exists() }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (forbiddenImports.any { token -> line.trimStart().startsWith(token) }) {
                        val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                        violations += "$relPath:${index + 1}: $line"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found legacy data/network imports in app startup path:")
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
        val domainRoots = rootDir.listFiles()
            ?.filter { file -> file.isDirectory && (file.name == "domain" || file.name.startsWith("domain-")) }
            ?.map { file -> file.resolve("src/main/java") }
            ?.filter { file -> file.exists() }
            .orEmpty()
        if (domainRoots.isEmpty()) return@doLast

        val violations = mutableListOf<String>()
        val packageRegex = Regex("""^\s*package\s+([A-Za-z0-9_.]+)\s*$""")

        domainRoots.forEach { domainRoot ->
            domainRoot.walkTopDown()
                .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
                .forEach fileLoop@{ file ->
                    val declaredPackage = file.useLines { lines ->
                        lines.mapNotNull { line -> packageRegex.matchEntire(line)?.groupValues?.get(1) }
                            .firstOrNull()
                    } ?: return@fileLoop

                    val expectedDir = declaredPackage.replace('.', '/')
                    val actualDir = file.parentFile.relativeTo(domainRoot).invariantSeparatorsPath
                    if (actualDir != expectedDir) {
                        val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                        violations += "$relPath: package=$declaredPackage path=$actualDir"
                    }
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

tasks.register("verifyDataPackageLayoutConsistency") {
    group = "verification"
    description = "Fail when data source file paths or package prefixes do not match their module context."

    doLast {
        val dataModules = rootDir.listFiles()
            ?.filter { file -> file.isDirectory && file.name.startsWith("data-") }
            .orEmpty()
        if (dataModules.isEmpty()) return@doLast

        val violations = mutableListOf<String>()
        val packageRegex = Regex("""^\s*package\s+([A-Za-z0-9_.]+)\s*$""")

        dataModules.forEach { moduleRoot ->
            val contextName = moduleRoot.name.removePrefix("data-").replace('-', '.')
            val requiredPackagePrefix = "com.chen.memorizewords.data.$contextName"
            val sourceRoots = listOf(
                moduleRoot.resolve("src/main/java")
            ).filter { file -> file.exists() }

            sourceRoots.forEach { sourceRoot ->
                sourceRoot.walkTopDown()
                    .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
                    .forEach fileLoop@{ file ->
                        val declaredPackage = file.useLines { lines ->
                            lines.mapNotNull { line -> packageRegex.matchEntire(line)?.groupValues?.get(1) }
                                .firstOrNull()
                        } ?: return@fileLoop

                        val actualDir = file.parentFile.relativeTo(sourceRoot).invariantSeparatorsPath
                        val expectedDir = declaredPackage.replace('.', '/')
                        val hasExpectedPrefix = declaredPackage == requiredPackagePrefix ||
                            declaredPackage.startsWith("$requiredPackagePrefix.")

                        if (actualDir != expectedDir || !hasExpectedPrefix) {
                            val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                            violations += "$relPath: package=$declaredPackage path=$actualDir requiredPrefix=$requiredPackagePrefix"
                        }
                    }
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found data files whose paths or package prefixes do not match their module context:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyLegacyArchitectureModulesRemoved") {
    group = "verification"
    description = "Fail when removed legacy architecture modules are included or present on disk."

    doLast {
        val legacyModules = listOf("domain", "data", "network")
        val settingsFile = rootDir.resolve("settings.gradle.kts")
        val settingsText = if (settingsFile.exists()) settingsFile.readText() else ""
        val violations = mutableListOf<String>()

        legacyModules.forEach { moduleName ->
            val includePattern = Regex("""include\(\s*":$moduleName"\s*\)""")
            if (includePattern.containsMatchIn(settingsText)) {
                violations += "settings.gradle.kts includes legacy module :$moduleName"
            }

            val moduleRoot = rootDir.resolve(moduleName)
            if (moduleRoot.exists()) {
                violations += "$moduleName/ still exists on disk"
            }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found legacy architecture modules that should be removed:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyRemovedArchitecturePackages") {
    group = "verification"
    description = "Fail when removed legacy or transitional architecture packages are referenced."

    doLast {
        val violations = mutableListOf<String>()
        val forbiddenPatterns = listOf(
            Regex("""com\.chen\.memorizewords\.domain\.(model|repository|usecase|service|orchestrator|query)\b"""),
            Regex("""com\.chen\.memorizewords\.data\.sync\.(legacy|network)\b""")
        )
        val ignoredSegments = listOf(
            "${File.separator}build${File.separator}",
            "${File.separator}.gradle${File.separator}"
        )

        rootDir.walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension in setOf("kt", "java", "xml", "kts") &&
                    ignoredSegments.none { segment -> file.path.contains(segment) }
            }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (forbiddenPatterns.any { pattern -> pattern.containsMatchIn(line) }) {
                        val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                        violations += "$relPath:${index + 1}: $line"
                    }
                }
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found removed architecture package references:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyCoreNetworkHasNoBusinessApi") {
    group = "verification"
    description = "Fail when core-network contains business API/DTO packages."

    doLast {
        val coreNetworkRoot = rootDir.resolve("core-network/src/main/java")
        if (!coreNetworkRoot.exists()) return@doLast

        val violations = coreNetworkRoot.walkTopDown()
            .filter { file -> file.isFile && file.extension in setOf("kt", "java") }
            .mapNotNull { file ->
                val relPath = file.relativeTo(coreNetworkRoot).invariantSeparatorsPath
                val packagePath = relPath.substringBeforeLast('/', missingDelimiterValue = "")
                if (
                    packagePath.contains("/api/") ||
                    packagePath.endsWith("/api") ||
                    packagePath.contains("/dto/") ||
                    packagePath.endsWith("/dto")
                ) {
                    file.relativeTo(rootDir).invariantSeparatorsPath
                } else {
                    null
                }
            }
            .toList()

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found business API/DTO code in core-network:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifySyncModuleScope") {
    group = "verification"
    description = "Fail when data-sync grows non-sync local Room or business API shells."

    doLast {
        val violations = mutableListOf<String>()
        val syncRoot = rootDir.resolve("data-sync/src/main/java/com/chen/memorizewords/data/sync")
        if (!syncRoot.exists()) return@doLast

        fun flagIfExists(relativePath: String) {
            val file = syncRoot.resolve(relativePath)
            if (file.exists()) {
                violations += "data-sync/src/main/java/com/chen/memorizewords/data/sync/$relativePath"
            }
        }

        listOf(
            "local/room/AppDatabase.kt",
            "local/room/AppDatabaseRuntimeGuards.kt",
            "local/room/Converters.kt",
            "remote/wordbook",
            "remote/practice",
            "remoteapi/api/wordbook",
            "remoteapi/api/practice"
        ).forEach(::flagIfExists)

        val modelRoot = syncRoot.resolve("local/room/model")
        modelRoot.listFiles()
            ?.filter { file -> file.isDirectory && file.name != "sync" }
            ?.forEach { file ->
                violations += file.relativeTo(rootDir).invariantSeparatorsPath
            }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found non-sync implementation code in data-sync:")
                    violations.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifyLocalAssetResetCoverage") {
    group = "verification"
    description = "Fail when destructive startup reset does not cover every new local database."

    doLast {
        val policyFile = rootDir.resolve(
            "app/src/main/java/com/chen/memorizewords/startup/LocalAssetResetPolicy.kt"
        )
        if (!policyFile.exists()) return@doLast

        val policyText = policyFile.readText()
        val expectedDatabaseNames = listOf(
            "memorize_words.db",
            "memorize_words_arch_v1.db",
            "memorize_words_arch_v1_account.db",
            "memorize_words_arch_v1_word.db",
            "memorize_words_arch_v1_wordbook.db",
            "memorize_words_arch_v1_study.db",
            "memorize_words_arch_v1_practice.db",
            "memorize_words_arch_v1_sync_outbox.db",
            "memorize_words_arch_v1_floating.db"
        )
        val missing = expectedDatabaseNames.filterNot { databaseName ->
            policyText.contains("\"$databaseName\"")
        }

        if (missing.isNotEmpty()) {
            error(
                buildString {
                    appendLine("LocalAssetResetPolicy is missing database names:")
                    missing.forEach { appendLine(it) }
                }
            )
        }
    }
}

tasks.register("verifySpeechModuleBoundaries") {
    group = "verification"
    description = "Fail when speech infrastructure depends on data implementation modules."

    doLast {
        val violations = mutableListOf<String>()
        val speechBuildFile = rootDir.resolve("speech/build.gradle.kts")
        if (speechBuildFile.exists()) {
            speechBuildFile.readLines().forEachIndexed { index, line ->
                if (line.contains("project(\":data")) {
                    violations += "speech/build.gradle.kts:${index + 1}: $line"
                }
            }
        }

        val speechSrcRoot = rootDir.resolve("speech/src/main/java")
        if (speechSrcRoot.exists()) {
            speechSrcRoot.walkTopDown()
                .filter { file -> file.isFile && (file.extension == "kt" || file.extension == "java") }
                .forEach { file ->
                    file.readLines().forEachIndexed { index, line ->
                        if (line.trimStart().startsWith("import com.chen.memorizewords.data.")) {
                            val relPath = file.relativeTo(rootDir).invariantSeparatorsPath
                            violations += "$relPath:${index + 1}: $line"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            error(
                buildString {
                    appendLine("Found data-layer dependencies in speech module:")
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
        dependsOn(rootProject.tasks.named("verifyAppCompositionBoundaries"))
        dependsOn(rootProject.tasks.named("verifyFeatureModuleProjectDependencies"))
        dependsOn(rootProject.tasks.named("verifyNewArchitectureProjectDependencies"))
        dependsOn(rootProject.tasks.named("verifyDataModuleImportBoundaries"))
        dependsOn(rootProject.tasks.named("verifyFeatureApplicationLayerBoundaries"))
        dependsOn(rootProject.tasks.named("verifyDomainPackageLayoutConsistency"))
        dependsOn(rootProject.tasks.named("verifyDataPackageLayoutConsistency"))
        dependsOn(rootProject.tasks.named("verifyLegacyArchitectureModulesRemoved"))
        dependsOn(rootProject.tasks.named("verifyRemovedArchitecturePackages"))
        dependsOn(rootProject.tasks.named("verifyCoreNetworkHasNoBusinessApi"))
        dependsOn(rootProject.tasks.named("verifySyncModuleScope"))
        dependsOn(rootProject.tasks.named("verifyLocalAssetResetCoverage"))
        dependsOn(rootProject.tasks.named("verifySpeechModuleBoundaries"))
        dependsOn(rootProject.tasks.named("verifyViewModelFrameworkLeakage"))
        dependsOn(rootProject.tasks.named("verifyUiEventContracts"))
        dependsOn(rootProject.tasks.named("verifyStartupPathIsolation"))
    }
}

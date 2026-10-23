import com.diffplug.gradle.spotless.SpotlessExtension
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern
import java.util.spi.ToolProvider
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.repositories

plugins {
    base
    // Keep shared plugin services on the root classloader for sibling projects.
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

val internalModuleProjectPrefix = ":modules:internal:"
val javaPackagePattern = Regex("(?m)^\\s*package\\s+([A-Za-z_][\\w.]*)\\s*;")
val javaImportPattern = Regex("(?m)^\\s*import\\s+(?:static\\s+)?([A-Za-z_][\\w.]*(?:\\.\\*)?)\\s*;")
val modulithApiAnnotationPattern = Regex("@(?:[A-Za-z_][\\w.]*\\.)?(?:ApplicationModule|NamedInterface)\\b")
val jdepsPackageDependencyPattern = Regex("^\\s*([A-Za-z_][\\w.]*)\\s+->\\s+([A-Za-z_][\\w.]*)\\s+.*$")

fun Project.mainJavaSources(): Sequence<File> {
    val sourceRoot = file("src/main/java")
    if (!sourceRoot.isDirectory) {
        return emptySequence()
    }
    return sourceRoot.walkTopDown().filter { it.isFile && it.extension == "java" }
}

fun Project.mainJavaPackages(): Set<String> = this
    .mainJavaSources()
    .mapNotNull { source -> javaPackagePattern.find(source.readText())?.groupValues?.get(1) }
    .toSet()

fun isOwnedByInternalModule(packageName: String, internalPackages: Set<String>): Boolean = internalPackages.any {
    packageName == it || packageName.startsWith("$it.")
}

fun Project.publicApiPackages(): Set<String> = this
    .mainJavaSources()
    .mapNotNull { source ->
        val sourceText = source.readText()
        val packageName = javaPackagePattern.find(sourceText)?.groupValues?.get(1) ?: return@mapNotNull null
        if (
            packageName.contains(".application.api") ||
            (source.name == "package-info.java" && modulithApiAnnotationPattern.containsMatchIn(sourceText))
        ) {
            packageName
        } else {
            null
        }
    }
    .toSet()

allprojects {
    group = "io.taskmigo"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    pluginManager.withPlugin("java") {
        pluginManager.apply("net.ltgt.errorprone")
        pluginManager.apply("checkstyle")
        pluginManager.apply("com.diffplug.spotless")

        repositories {
            mavenCentral()
        }

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(26)
            }
        }

        dependencies {
            add("testRuntimeOnly", libs.junit.platform.launcher)
            add("errorprone", libs.errorprone.core)
            add("errorprone", libs.nullaway)
        }

        extensions.configure<SpotlessExtension> {
            java {
                shortenFullyQualifiedTypes()
                importOrder()
                removeUnusedImports()
                cleanthat().sourceCompatibility("26")
            }
        }

        extensions.configure<CheckstyleExtension> {
            toolVersion = libs.versions.checkstyle.get()
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        }

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disableAllChecks.set(true)
                excludedPaths.set(".*/build/generated/.*")
                error(
                    "JSpecifyUnrecognizedAnnotationLocation",
                    "NullAway",
                    "RequireExplicitNullMarking",
                )
                option("NullAway:OnlyNullMarked", "true")
                option("NullAway:JSpecifyMode", "true")
                option("NullAway:JSpecifyExperimental", "true")
                option("NullAway:HandleTestAssertionLibraries", "true")
            }
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                exceptionFormat = TestExceptionFormat.FULL
            }
        }

        tasks.withType<Checkstyle>().configureEach {
            reports {
                xml.required.set(false)
                html.required.set(true)
            }
        }

        if (!pluginManager.hasPlugin("java-library")) {
            dependencies {
                add("compileOnly", libs.jspecify)
            }
        }

        rootProject.tasks.build {
            dependsOn(tasks.build)
        }
    }

    pluginManager.withPlugin("java-library") {
        dependencies {
            add("compileOnlyApi", libs.jspecify)
        }
    }
}

val verifyInternalProjectDependencies = tasks.register("verifyInternalProjectDependencies") {
    group = "verification"
    description = "Rejects forbidden direct Gradle dependencies on internal server modules."

    doLast {
        val violations = mutableListOf<String>()

        subprojects.sortedBy { it.path }.forEach projectLoop@ { sourceProject ->
            val isApp = sourceProject.path.startsWith(":apps:")
            val isProductionModule =
                sourceProject.path.startsWith(":modules:") && !sourceProject.path.startsWith(internalModuleProjectPrefix)
            if (!isApp && !isProductionModule) {
                return@projectLoop
            }

            sourceProject.configurations.sortedBy { it.name }.forEach configurationLoop@ { configuration ->
                if (isProductionModule && configuration.name !in setOf("api", "compileOnlyApi")) {
                    return@configurationLoop
                }
                configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                    if (dependency.path.startsWith(internalModuleProjectPrefix)) {
                        violations.add(
                            "${sourceProject.path}:${configuration.name} -> ${dependency.path}" +
                                if (isApp) {
                                    " (apps must not depend directly on internal modules)"
                                } else {
                                    " (production modules must not export internal modules)"
                                }
                        )
                    }
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Internal module dependency boundary violations:\n" + violations.joinToString("\n") { " - $it" }
            )
        }
    }
}

val verifyApplicationInternalImports = tasks.register("verifyApplicationInternalImports") {
    group = "verification"
    description = "Rejects production app source imports from internal server modules."

    doLast {
        val internalPackages = subprojects
            .filter { it.path.startsWith(internalModuleProjectPrefix) }
            .flatMap { it.mainJavaPackages() }
            .toSet()
        val violations = mutableListOf<String>()

        subprojects
            .filter { it.path.startsWith(":apps:") }
            .sortedBy { it.path }
            .forEach { application ->
                application.mainJavaSources().forEach { source ->
                    val sourceText = source.readText()
                    javaImportPattern.findAll(sourceText).forEach { importMatch ->
                        val importedName = importMatch.groupValues[1].removeSuffix(".*")
                        if (isOwnedByInternalModule(importedName, internalPackages)) {
                            violations.add(
                                "${source.relativeTo(rootProject.projectDir)} imports $importedName"
                            )
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Applications must not import internal module packages:\n" + violations.joinToString("\n") { " - $it" }
            )
        }
    }
}

val verifyInternalApiBoundaries = tasks.register("verifyInternalApiBoundaries") {
    group = "verification"
    description = "Rejects internal module types from public application APIs and Modulith named interfaces."

    doLast {
        val internalPackages = subprojects
            .filter { it.path.startsWith(internalModuleProjectPrefix) }
            .flatMap { it.mainJavaPackages() }
            .toSet()
        val jdeps = ToolProvider.findFirst("jdeps").orElseThrow {
            GradleException("The JDK jdeps tool is required to verify internal API boundaries")
        }
        val violations = mutableListOf<String>()

        subprojects
            .filter {
                it.path.startsWith(":modules:") && !it.path.startsWith(internalModuleProjectPrefix)
            }
            .sortedBy { it.path }
            .forEach moduleLoop@ { module ->
                val publicPackages = module.publicApiPackages()
                if (publicPackages.isEmpty()) {
                    return@moduleLoop
                }

                val includePattern = publicPackages
                    .sorted()
                    .joinToString(prefix = "^(?:", postfix = ")$", separator = "|") { Pattern.quote(it) }
                val jarFile = module.tasks.named("jar", Jar::class.java).get().archiveFile.get().asFile
                val stdout = StringWriter()
                val stderr = StringWriter()
                val stdoutWriter = PrintWriter(stdout)
                val stderrWriter = PrintWriter(stderr)
                val exitCode = jdeps.run(
                    stdoutWriter,
                    stderrWriter,
                    "--api-only",
                    "--ignore-missing-deps",
                    "--no-recursive",
                    "-include",
                    includePattern,
                    "-verbose:package",
                    jarFile.absolutePath,
                )
                stdoutWriter.flush()
                stderrWriter.flush()
                if (exitCode != 0) {
                    throw GradleException(
                        "jdeps failed for ${module.path} (${jarFile.name}):\n${stderr.toString().trim()}"
                    )
                }

                stdout.toString().lineSequence().forEach lineLoop@ { line ->
                    val match = jdepsPackageDependencyPattern.matchEntire(line) ?: return@lineLoop
                    val sourcePackage = match.groupValues[1]
                    val targetPackage = match.groupValues[2]
                    if (
                        sourcePackage in publicPackages &&
                        isOwnedByInternalModule(targetPackage, internalPackages)
                    ) {
                        violations.add("${module.path}: $sourcePackage -> $targetPackage")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Public APIs must not expose internal module types:\n" + violations.joinToString("\n") { " - $it" }
            )
        }
    }
}

gradle.projectsEvaluated {
    val productionModuleJarTasks = subprojects
        .filter {
            it.path.startsWith(":modules:") && !it.path.startsWith(internalModuleProjectPrefix)
        }
        .mapNotNull { module ->
            if (module.pluginManager.hasPlugin("java")) {
                module.tasks.named("jar", Jar::class.java)
            } else {
                null
            }
        }
    verifyInternalApiBoundaries.configure {
        dependsOn(productionModuleJarTasks)
    }
}

val verifyInternalModuleBoundaries = tasks.register("verifyInternalModuleBoundaries") {
    group = "verification"
    description = "Verifies all internal server module boundaries."
    dependsOn(
        verifyInternalProjectDependencies,
        verifyApplicationInternalImports,
        verifyInternalApiBoundaries,
    )
}

tasks.check {
    dependsOn(verifyInternalModuleBoundaries)
}

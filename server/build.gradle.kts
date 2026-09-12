import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "io.taskmigo"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withType<JavaPlugin> {
        apply(plugin = "net.ltgt.errorprone")
        apply(plugin = "checkstyle")
        apply(plugin = "com.diffplug.spotless")

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

        tasks.withType<JavaCompile>().configureEach {
            options.errorprone {
                disableAllChecks.set(true)
                error("NullAway", "RequireExplicitNullMarking")
                option("NullAway:OnlyNullMarked", "true")
                option("NullAway:JSpecifyMode", "true")
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
    }
}

tasks.named("build") {
    dependsOn(
        subprojects.flatMap { project ->
            project.subprojects.ifEmpty { setOf(project) }.mapNotNull { leaf ->
                leaf.tasks.findByName("build")?.path
            }
        }
    )
}

tasks.register("verifyResolvedModuleArchitecture") {
    group = "verification"
    description = "Verifies resolved production dependencies against the normative module boundaries."

    doLast {
        val allowedDirect = mapOf(
            "foundation" to emptySet(),
            "language" to emptySet(),
            "database" to emptySet(),
            "query" to setOf("language"),
            "authorization" to setOf("foundation", "language"),
            "identity" to setOf("foundation", "language", "query", "authorization", "database")
        )
        val graph = mutableMapOf<String, Set<String>>()
        val errors = mutableListOf<String>()

        for ((moduleName, allowed) in allowedDirect) {
            val module = project(":modules:$moduleName")
            val configuration = module.configurations.getByName("runtimeClasspath")
            val resolution = configuration.incoming.resolutionResult
            val direct = resolution.root.dependencies.asSequence()
                .filterIsInstance<ResolvedDependencyResult>()
                .mapNotNull { (it.selected.id as? ProjectComponentIdentifier)?.projectPath }
                .filter { it.startsWith(":modules:") }
                .map { it.removePrefix(":modules:") }
                .toSet()
            graph[moduleName] = direct
            val prohibited = direct - allowed
            if (prohibited.isNotEmpty()) {
                errors += "$moduleName declares prohibited direct project dependencies: ${prohibited.sorted()}"
            }

        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(module: String, trail: List<String>) {
            if (!visiting.add(module)) {
                errors += "module dependency cycle: ${(trail + module).joinToString(" -> ")}"
                return
            }
            for (dependency in graph[module].orEmpty().sorted()) {
                if (dependency in graph) visit(dependency, trail + module)
            }
            visiting.remove(module)
            visited.add(module)
        }
        graph.keys.forEach { if (it !in visited) visit(it, emptyList()) }

        for (moduleName in listOf("foundation", "language", "database", "query", "authorization", "identity")) {
            val module = project(":modules:$moduleName")
            val projects = module.configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents
                .mapNotNull { (it.id as? ProjectComponentIdentifier)?.projectPath }
            val forbidden = when (moduleName) {
                "foundation" -> projects.filterNot { it == ":modules:foundation" }
                "query" -> projects.filter { it in setOf(":modules:authorization", ":modules:identity", ":apps:web") }
                "authorization" -> projects.filter { it in setOf(":modules:identity", ":modules:query", ":apps:web") }
                else -> emptyList()
            }
            forbidden.distinct().sorted().forEach { dependency ->
                errors += "$moduleName resolves prohibited transitive project dependency: $dependency"
            }
        }

        check(errors.isEmpty()) { "Resolved module architecture verification failed:\n- ${errors.joinToString("\n- ")}" }
        logger.lifecycle("Resolved module architecture verification passed for ${allowedDirect.keys.sorted().joinToString(", ")}.")
    }
}

import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult

plugins {
    base
    // Keep shared plugin services on the root classloader for sibling projects.
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "io.taskmigo"
    version = "0.0.1-SNAPSHOT"
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

val verifyBuildConventionScopes = tasks.register("verifyBuildConventionScopes") {
    group = "verification"
    description = "Verifies the fixed build-convention versions, scopes, and consumers."

    doLast {
        val actualVersions = mapOf(
            "spring-modulith" to libs.versions.spring.modulith.get(),
            "archunit" to libs.versions.archunit.get(),
            "errorprone-gradle-plugin" to libs.versions.errorprone.gradle.plugin.get(),
            "errorprone-core" to libs.versions.errorprone.core.get(),
            "nullaway" to libs.versions.nullaway.get(),
            "jspecify" to libs.versions.jspecify.get(),
            "checkstyle" to libs.versions.checkstyle.get(),
            "spotless" to libs.versions.spotless.get()
        )
        val expectedVersions = mapOf(
            "spring-modulith" to "2.1.0",
            "archunit" to "1.4.2",
            "errorprone-gradle-plugin" to "5.1.0",
            "errorprone-core" to "2.50.0",
            "nullaway" to "0.14.0",
            "jspecify" to "1.0.1",
            "checkstyle" to "14.1.0",
            "spotless" to "8.10.1"
        )
        for ((name, expected) in expectedVersions) {
            check(actualVersions[name] == expected) {
                "$name is ${actualVersions[name]}, expected fixed version $expected"
            }
        }

        val wrapperProperties = rootProject.file("gradle/wrapper/gradle-wrapper.properties").readText()
        check("distributionUrl=https\\://services.gradle.org/distributions/gradle-9.7.1-bin.zip" in wrapperProperties) {
            "Gradle wrapper must use version 9.7.1"
        }
        check("distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a" in wrapperProperties) {
            "Gradle wrapper must pin the normative distribution checksum"
        }

        val conventionPlugins = mapOf(
            ":modules:foundation" to "taskmigo.java-library",
            ":modules:database" to "taskmigo.java-library",
            ":modules:language" to "taskmigo.spring-module",
            ":modules:query" to "taskmigo.spring-module",
            ":modules:authorization" to "taskmigo.spring-module",
            ":modules:identity" to "taskmigo.spring-module",
            ":benchmarks:authorization" to "taskmigo.java-base",
            ":apps:bootstrap" to "taskmigo.spring-application",
            ":apps:web" to "taskmigo.spring-application",
            ":apps:worker" to "taskmigo.spring-application"
        )
        for ((projectPath, pluginId) in conventionPlugins) {
            check(project(projectPath).pluginManager.hasPlugin(pluginId)) {
                "$projectPath must apply $pluginId"
            }
        }

        fun Project.directDependencies(configurationName: String): Set<String> =
            configurations.getByName(configurationName).dependencies.mapNotNull { dependency ->
                dependency.group?.let { group -> "$group:${dependency.name}" }
            }.toSet()

        val reusableLibraries = listOf(
            project(":modules:foundation"),
            project(":modules:database"),
            project(":modules:language"),
            project(":modules:query"),
            project(":modules:authorization"),
            project(":modules:identity")
        )
        val nonPublishedJavaProjects = listOf(
            project(":benchmarks:authorization")
        )
        reusableLibraries.forEach { library ->
            check("org.jspecify:jspecify" in library.directDependencies("compileOnlyApi")) {
                "${library.path} must receive JSpecify through compileOnlyApi"
            }
            check("org.jspecify:jspecify" !in library.directDependencies("api")) {
                "${library.path} must not expose JSpecify through api"
            }
        }

        val applications = listOf(
            project(":apps:bootstrap"),
            project(":apps:web"),
            project(":apps:worker")
        )
        applications.forEach { application ->
            check("org.jspecify:jspecify" in application.directDependencies("compileOnly")) {
                "${application.path} must receive JSpecify through compileOnly"
            }
            check("org.jspecify:jspecify" in application.directDependencies("runtimeOnly")) {
                "${application.path} must package JSpecify because Spring runtime metadata inspects it"
            }
            check("org.jspecify:jspecify" !in application.directDependencies("implementation")) {
                "${application.path} must not put JSpecify on its runtime classpath"
            }
        }

        nonPublishedJavaProjects.forEach { javaProject ->
            check("org.jspecify:jspecify" in javaProject.directDependencies("compileOnly")) {
                "${javaProject.path} must receive JSpecify through compileOnly"
            }
            check("org.jspecify:jspecify" !in javaProject.directDependencies("implementation")) {
                "${javaProject.path} must not put JSpecify on its runtime classpath"
            }
        }

        (reusableLibraries + nonPublishedJavaProjects + applications).forEach { javaProject ->
            check(
                setOf("com.google.errorprone:error_prone_core", "com.uber.nullaway:nullaway")
                    .all { it in javaProject.directDependencies("errorprone") }
            ) { "${javaProject.path} must configure Error Prone and NullAway through the errorprone tool scope" }
        }

        val buildOnlyCoordinates = setOf(
            "com.google.errorprone:error_prone_core",
            "com.uber.nullaway:nullaway",
            "com.puppycrawl.tools:checkstyle",
            "com.tngtech.archunit:archunit-junit5",
            "org.springframework.modulith:spring-modulith-starter-core",
            "org.springframework.modulith:spring-modulith-starter-test"
        )
        (reusableLibraries + nonPublishedJavaProjects + applications).forEach { javaProject ->
            val leaked = javaProject.configurations.getByName("runtimeClasspath").incoming.resolutionResult.allComponents
                .mapNotNull { component ->
                    (component.id as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}" }
                }
                .filter { it in buildOnlyCoordinates }
                .distinct()
                .sorted()
            check(leaked.isEmpty()) {
                "${javaProject.path} leaks build-only dependencies onto runtimeClasspath: $leaked"
            }
        }

        logger.lifecycle("Build convention version and scope verification passed.")
    }
}

tasks.register("verifyResolvedModuleArchitecture") {
    group = "verification"
    description = "Verifies resolved production dependencies against the normative module boundaries."

    dependsOn(verifyBuildConventionScopes)

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

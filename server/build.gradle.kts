import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.sonarqube)
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
            add("implementation", platform(libs.spring.boot.bom))
            add("implementation", platform(libs.spring.modulith.bom))
            add("testImplementation", platform(libs.spring.boot.bom))
            add("testImplementation", platform(libs.spring.modulith.bom))
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

val verifyDatabaseLifecycleOwnership = tasks.register("verifyDatabaseLifecycleOwnership") {
    group = "verification"
    description = "Ensures database migrations and shared database configuration stay centralized"

    doLast {
        val migrationPrefix = "modules/database/src/main/resources/db/migration/"
        val misplacedMigrations = fileTree(rootDir) {
            include("**/src/main/resources/db/migration/**")
        }.files
            .map { it.relativeTo(rootDir).invariantSeparatorsPath }
            .filterNot { it.startsWith(migrationPrefix) }
            .sorted()

        check(misplacedMigrations.isEmpty()) {
            "Flyway migrations must live under $migrationPrefix: ${misplacedMigrations.joinToString()}"
        }

        val databaseConfigs = fileTree(rootDir) {
            include("**/src/main/resources/application-database.yaml")
        }.files.map { it.relativeTo(rootDir).invariantSeparatorsPath }.sorted()

        check(databaseConfigs == listOf("modules/database/src/main/resources/application-database.yaml")) {
            "Shared database configuration must have exactly one owner: ${databaseConfigs.joinToString()}"
        }
    }
}

tasks.named("build") {
    dependsOn(verifyDatabaseLifecycleOwnership)
    dependsOn(
        subprojects.flatMap { project ->
            project.subprojects.ifEmpty { setOf(project) }.mapNotNull { leaf ->
                leaf.tasks.findByName("build")?.path
            }
        }
    )
}

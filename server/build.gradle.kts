import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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
                error("NullAway", "RequireExplicitNullMarking")
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

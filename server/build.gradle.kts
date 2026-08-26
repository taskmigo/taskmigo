import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    base
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.errorprone) apply false
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

tasks.named("build") {
    dependsOn(
        subprojects.flatMap { project ->
            project.subprojects.ifEmpty { setOf(project) }.mapNotNull { leaf ->
                leaf.tasks.findByName("build")?.path
            }
        }
    )
}

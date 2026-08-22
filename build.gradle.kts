import com.diffplug.gradle.spotless.SpotlessExtension
import net.ltgt.gradle.errorprone.errorprone

plugins {
    base
    id("com.diffplug.spotless") version "8.10.0" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("net.ltgt.errorprone") version "5.1.0" apply false
}

allprojects {
    group = "io.taskmigo"
    version = "0.0.1-SNAPSHOT"
}

subprojects {
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    extensions.configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            prettier(
                mapOf(
                    "prettier" to "3.9.6",
                    "prettier-plugin-java" to "2.10.3",
                ),
            ).config(
                mapOf(
                    "parser" to "java",
                    "plugins" to listOf("prettier-plugin-java"),
                    "printWidth" to 120,
                    "tabWidth" to 4,
                ),
            )
        }
    }

    plugins.withType<JavaPlugin> {
        apply(plugin = "net.ltgt.errorprone")

        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion = JavaLanguageVersion.of(26)
            }
        }

        dependencies {
            add("implementation", platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
            add("implementation", platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))
            add("testImplementation", platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
            add("testImplementation", platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))
            add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
            add("errorprone", "com.google.errorprone:error_prone_core:2.50.0")
            add("errorprone", "com.uber.nullaway:nullaway:0.13.8")
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
        }
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

import net.ltgt.gradle.errorprone.errorprone

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
        }
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

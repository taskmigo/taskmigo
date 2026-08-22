plugins {
    base
    id("org.springframework.boot") version "4.1.1" apply false
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
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

import org.gradle.api.tasks.testing.Test

plugins {
    java
}

description = "Taskmigo build-time OpenAPI generator"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":apps:web"))
    implementation(project(":modules:query"))
    implementation(libs.springdoc.openapi.starter.webmvc.api)
    implementation(libs.spring.boot.starter.webmvc)

    testImplementation(project(":modules:authorization"))
    testImplementation(project(":modules:identity"))
    testImplementation(libs.scalar.webmvc)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.spring.boot.starter.oauth2.authorization.server)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("io.taskmigo.openapi.OpenApiGenerationIntegrationTest")
        isFailOnNoMatchingTests = false
    }
}

val openApiSnapshot = project(":apps:web").layout.projectDirectory.file(
    "src/main/resources/static/api/docs/openapi.yaml"
)

tasks.register<Test>("generateOpenApi") {
    group = "documentation"
    description = "Generates the committed OpenAPI YAML from the application contract."
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("io.taskmigo.openapi.OpenApiGenerationIntegrationTest")
    }
    systemProperty("taskmigo.openapi.output", openApiSnapshot.asFile.absolutePath)
    outputs.file(openApiSnapshot)
    outputs.upToDateWhen { false }
}

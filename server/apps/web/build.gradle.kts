import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    java
    alias(libs.plugins.spring.boot)
}

tasks.bootJar {
    archiveFileName = "web.jar"
}

description = "Taskmigo HTTP and OAuth application"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":testing:architecture"))

    implementation(platform(libs.spring.boot.bom))
    implementation(project(":modules:foundation"))
    implementation(project(":modules:query"))
    implementation(project(":modules:access-control"))
    // Provides shared datasource/JPA configuration; apps/migration owns migration execution.
    implementation(project(":modules:database"))
    implementation(project(":modules:identity"))
    runtimeOnly(libs.jspecify)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.swagger.annotations.jakarta)
    implementation(libs.scalar.webmvc)

    testImplementation(libs.springdoc.openapi.starter.webmvc.api)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

tasks.named<Test>("test") {
    filter {
        excludeTestsMatching("io.taskmigo.web.adapter.in.http.api.OpenApiGenerationIntegrationTest")
    }
}

tasks.register<Test>("generateOpenApi") {
    group = "documentation"
    description = "Generates the committed OpenAPI YAML from the application contract."
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("io.taskmigo.web.adapter.in.http.api.OpenApiGenerationIntegrationTest")
    }
    systemProperty("taskmigo.openapi.generate", "true")
    outputs.upToDateWhen { false }
}

tasks.register<Test>("verifyOpenApi") {
    group = "verification"
    description = "Fails when the committed OpenAPI YAML is stale."
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("io.taskmigo.web.adapter.in.http.api.OpenApiGenerationIntegrationTest")
    }
    systemProperty("taskmigo.openapi.verify", "true")
    outputs.upToDateWhen { false }
}

import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    id("org.springframework.boot")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("web.jar")
}

description = "Taskmigo HTTP and OAuth application"

dependencies {
    implementation(libs.jspecify)
    implementation(project(":modules:foundation"))
    // Provides shared datasource/JPA configuration; apps/bootstrap owns migration execution.
    implementation(project(":modules:database"))
    implementation(project(":modules:auth"))
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.springdoc.openapi.starter.webmvc.scalar)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-parameters")
}

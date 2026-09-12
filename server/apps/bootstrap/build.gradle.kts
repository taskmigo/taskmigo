import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    java
    alias(libs.plugins.spring.boot)
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("bootstrap.jar")
}

description = "Taskmigo database migration and installation bootstrap application"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)

    implementation(platform(libs.spring.boot.bom))
    implementation(project(":modules:database"))
    implementation(project(":modules:authorization"))
    implementation(project(":modules:identity"))
    runtimeOnly(libs.jspecify)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.security.oauth2.authorization.server)
    implementation(libs.jackson.dataformat.yaml)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

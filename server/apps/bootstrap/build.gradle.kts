import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    id("taskmigo.spring-application")
}

tasks.named<BootJar>("bootJar") {
    archiveFileName.set("bootstrap.jar")
}

description = "Taskmigo database migration and installation bootstrap application"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":modules:database"))
    implementation(project(":modules:authorization"))
    implementation(project(":modules:identity"))
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

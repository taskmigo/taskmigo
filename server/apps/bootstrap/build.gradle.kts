plugins {
    java
    id("org.springframework.boot")
}

description = "Taskmigo bootstrap application"

dependencies {
    implementation(project(":apps:web"))
    implementation(project(":modules:authorization"))
    implementation(project(":modules:organization"))
    implementation(project(":modules:project"))
    implementation(project(":modules:database"))
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)
    implementation(libs.spring.boot.starter.webmvc)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql.driver)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

plugins {
    java
    id("org.springframework.boot")
}

description = "Taskmigo database migration and installation bootstrap application"

dependencies {
    implementation(libs.jspecify)
    implementation(project(":modules:database"))
    implementation(project(":modules:identity"))
    implementation(project(":modules:organization"))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.starter.flyway)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

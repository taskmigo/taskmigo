plugins {
    `java-library`
}

description = "Shared HTTP API infrastructure"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))

    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.springdoc.openapi.starter.webmvc.scalar)
}

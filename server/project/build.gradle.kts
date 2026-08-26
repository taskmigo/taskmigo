plugins {
    `java-library`
}

description = "Projects, memberships, authorization, and project history"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":organization"))

    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)

    runtimeOnly(libs.postgresql.driver)
}

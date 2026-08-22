plugins {
    `java-library`
}

description = "Shared Taskmigo domain and persistence modules"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.postgresql)

    runtimeOnly(libs.postgresql.driver)
}

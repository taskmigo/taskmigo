plugins {
    `java-library`
}

description = "Organizations, users, and groups"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))

    implementation(libs.spring.boot.starter.data.jpa)
}

plugins {
    `java-library`
}

description = "Organization, users, groups, roles, and permissions"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    api(project(":modules:foundation"))

    implementation(libs.spring.boot.starter.data.jpa)
}

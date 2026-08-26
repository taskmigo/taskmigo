plugins {
    `java-library`
}

description = "Organization, users, groups, roles, and permissions"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)

    implementation(libs.spring.boot.starter.data.jpa)
}

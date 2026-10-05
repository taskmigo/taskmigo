plugins {
    `java-library`
}

description = "Shared JPA-backed operation query schema infrastructure"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation:core"))
    api(project(":modules:query"))
    api(libs.jakarta.persistence.api)
    implementation(libs.spring.boot.core.starter)

    testImplementation(libs.spring.boot.starter.test)
}

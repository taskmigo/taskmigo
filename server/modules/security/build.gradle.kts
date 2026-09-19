plugins {
    `java-library`
}

description = "Shared Spring Security and OAuth persistence infrastructure"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)
    api(libs.spring.security.oauth2.authorization.server)
    implementation(libs.spring.security.crypto)
    implementation(libs.jackson.databind)

    testImplementation(libs.spring.boot.starter.test)
}

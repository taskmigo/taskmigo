plugins {
    `java-library`
}

description = "Shared Spring Security infrastructure"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.security.crypto)

    testImplementation(libs.spring.boot.starter.test)
}

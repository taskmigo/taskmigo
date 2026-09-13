plugins {
    `java-library`
}

description = "Taskmigo identity resources, persistence, and authorization integration"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation"))
    api(project(":modules:query"))
    api(project(":modules:authorization"))
    implementation(project(":modules:language"))

    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.jackson.databind)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.security.oauth2.authorization.server)
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
}

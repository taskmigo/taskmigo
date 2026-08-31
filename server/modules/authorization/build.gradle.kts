plugins {
    `java-library`
}

description = "API ACL policy DSL and query planning primitives"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    implementation(libs.spring.boot.core.starter)

    testImplementation(libs.spring.boot.starter.test)
}

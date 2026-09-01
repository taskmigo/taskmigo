plugins {
    `java-library`
}

description = "Data-driven API authorization resources, graph resolution, and query planning"

dependencies {
    api(libs.jspecify)
    api(libs.spring.modulith.starter.core)
    implementation(libs.re2j)
    implementation(libs.spring.boot.core.starter)
    implementation(libs.spring.boot.starter.data.jpa)

    testImplementation(libs.spring.boot.starter.test)
}

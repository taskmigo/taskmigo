plugins {
    `java-library`
}

description = "Taskmigo Authorization semantics and public contracts"

dependencies {
    api(libs.jspecify)
    api(libs.spring.boot.core.starter)
    api(libs.spring.modulith.starter.core)
    api("com.fasterxml.jackson.core:jackson-annotations")
    api(project(":modules:foundation"))
    api(project(":modules:language"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

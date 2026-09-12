plugins {
    `java-library`
}

description = "Taskmigo Authorization semantics and public contracts"

dependencies {
    api(libs.jspecify)
    implementation(platform(libs.spring.boot.bom))
    implementation(platform(libs.spring.modulith.bom))
    api(libs.spring.boot.core.starter)
    compileOnly(libs.spring.modulith.starter.core)
    api("com.fasterxml.jackson.core:jackson-annotations")
    api(project(":modules:foundation"))
    api(project(":modules:language"))
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

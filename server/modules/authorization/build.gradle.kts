plugins {
    `java-library`
}

description = "Taskmigo Authorization semantics and public contracts"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)

    implementation(platform(libs.spring.boot.bom))
    api(libs.spring.boot.core.starter)
    api("com.fasterxml.jackson.core:jackson-annotations")
    api(project(":modules:foundation"))
    api(project(":modules:language"))
    implementation(platform(libs.guava.bom))
    implementation(libs.guava)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

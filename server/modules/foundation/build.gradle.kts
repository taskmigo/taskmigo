plugins {
    `java-library`
}

description = "Framework-neutral Taskmigo primitives and contracts"

dependencies {
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation(libs.archunit.junit5)
}

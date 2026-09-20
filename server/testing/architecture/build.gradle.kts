plugins {
    `java-library`
}

description = "Shared Hexagonal and Onion architecture test conventions"

dependencies {
    api(libs.archunit.junit5)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

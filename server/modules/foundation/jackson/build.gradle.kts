plugins {
    `java-library`
}

description = "Taskmigo shared Jackson serialization policy"

dependencies {
    api(platform(libs.spring.boot.bom))
    api(libs.jackson.databind)

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

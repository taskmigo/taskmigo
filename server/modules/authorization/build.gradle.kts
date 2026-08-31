plugins {
    `java-library`
}

description = "API ACL policy DSL and query planning primitives"

dependencies {
    api(libs.jspecify)

    testImplementation(libs.spring.boot.starter.test)
}

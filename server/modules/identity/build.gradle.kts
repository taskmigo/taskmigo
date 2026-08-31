plugins {
    `java-library`
}

description = "Shared OAuth identity contracts"

dependencies {
    implementation(libs.jspecify)
    api(libs.spring.boot.starter.oauth2.authorization.server)
}

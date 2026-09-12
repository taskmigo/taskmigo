plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.errorprone.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.spring.boot.gradle.plugin)
}

plugins {
    `java-library`
}

description = "Taskmigo Query Filtering contracts, validation, and filterBy compilation"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.archunit.junit5)

    implementation(platform(libs.spring.boot.bom))
    api(project(":modules:foundation"))
    implementation(libs.spring.boot.core.starter)
    api(project(":modules:language"))

    testImplementation(libs.spring.boot.starter.test)
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

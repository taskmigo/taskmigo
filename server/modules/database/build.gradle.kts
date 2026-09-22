plugins {
    `java-library`
}

description = "Shared database configuration, migrations, and PostgreSQL runtime support"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)

    api(platform(libs.spring.boot.bom))
    api(libs.jakarta.persistence.api)
    implementation(libs.spring.boot.starter.data.jpa)

    runtimeOnly(libs.postgresql.driver)

    testImplementation(libs.archunit.junit5)
    testImplementation("org.junit.jupiter:junit-jupiter")
}

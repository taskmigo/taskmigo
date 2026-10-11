plugins {
    `java-library`
}

description = "Database runtime configuration, migrations, and PostgreSQL support"

dependencies {
    runtimeOnly(platform(libs.spring.boot.bom))
    runtimeOnly(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql.driver)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(libs.archunit.junit5)
    testImplementation("org.junit.jupiter:junit-jupiter")
}

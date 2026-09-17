plugins {
    `java-library`
}

description = "Shared database configuration, migrations, and PostgreSQL runtime support"

dependencies {
    implementation(platform(libs.spring.boot.bom))
    api(libs.spring.boot.starter.data.jpa)

    runtimeOnly(libs.postgresql.driver)

    testImplementation(libs.archunit.junit5)
    testImplementation("org.junit.jupiter:junit-jupiter")
}

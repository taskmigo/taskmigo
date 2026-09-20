plugins {
    `java-library`
}

description = "Taskmigo-specific Checkstyle extensions"

dependencies {
    api("com.puppycrawl.tools:checkstyle:${libs.versions.checkstyle.get()}")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

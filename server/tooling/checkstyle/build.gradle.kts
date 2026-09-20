plugins {
    java
}

description = "Taskmigo-specific Checkstyle extensions"

dependencies {
    compileOnly("com.puppycrawl.tools:checkstyle:${libs.versions.checkstyle.get()}")

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("com.puppycrawl.tools:checkstyle:${libs.versions.checkstyle.get()}")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

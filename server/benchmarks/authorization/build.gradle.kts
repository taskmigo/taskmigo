plugins {
    java
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks for the Taskmigo Language compiler"

dependencies {
    jmhImplementation(project(":modules:language"))
    testImplementation(platform(libs.spring.boot.bom))
}

jmh {
    jmhVersion = "1.37"
}

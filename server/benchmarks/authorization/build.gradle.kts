plugins {
    `java-library`
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks for the Taskmigo Embedded Language compiler"

dependencies {
    jmhImplementation(project(":modules:embedded-language"))
}

jmh {
    jmhVersion = "1.37"
}

plugins {
    `java-library`
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks for the Taskmigo Language compiler"

dependencies {
    jmhImplementation(project(":modules:language"))
}

jmh {
    jmhVersion = "1.37"
}

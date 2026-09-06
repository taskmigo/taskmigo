plugins {
    `java-library`
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks for the Taskmigo Policy Language compiler"

dependencies {
    jmhImplementation(project(":modules:policy"))
}

jmh {
    jmhVersion = "1.37"
}

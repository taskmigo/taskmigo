plugins {
    `java-library`
    alias(libs.plugins.jmh)
}

description = "JMH benchmarks for authorization compiler"

dependencies {
    jmhImplementation(project(":modules:auth"))
}

jmh {
    jmhVersion = "1.37"
}

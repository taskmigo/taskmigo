plugins {
    `java-library`
    id("taskmigo.java-base")
}

configurations.matching { configuration ->
    configuration.name == "runtimeClasspath" || configuration.name == "runtimeElements"
}.configureEach {
    // Reusable libraries expose JSpecify only as a compile-time API contract.
    exclude(group = "org.jspecify", module = "jspecify")
}

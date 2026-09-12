import org.gradle.api.plugins.quality.Checkstyle

plugins {
    `java-library`
    antlr
}

tasks.generateGrammarSource {
    arguments.add("-visitor")
}

spotless {
    java {
        targetExclude("build/generated-src/**")
    }
}

tasks.withType<Checkstyle>().configureEach {
    exclude("**/build/generated-src/**")
    exclude("**/EmbeddedLanguage*.java")
}

configurations.runtimeClasspath {
    exclude(group = "org.antlr", module = "antlr4")
    exclude(group = "org.antlr", module = "ST4")
    exclude(group = "org.antlr", module = "antlr-runtime")
    exclude(group = "org.abego.treelayout", module = "org.abego.treelayout.core")
    exclude(group = "com.ibm.icu", module = "icu4j")
}

configurations.runtimeElements {
    exclude(group = "org.antlr", module = "antlr4")
    exclude(group = "org.antlr", module = "ST4")
    exclude(group = "org.antlr", module = "antlr-runtime")
    exclude(group = "org.abego.treelayout", module = "org.abego.treelayout.core")
    exclude(group = "com.ibm.icu", module = "icu4j")
}

description = "Taskmigo Language"

dependencies {
    compileOnly(platform(libs.spring.modulith.bom))
    compileOnly(libs.spring.modulith.starter.core)
    testImplementation(platform(libs.spring.modulith.bom))
    testImplementation(libs.spring.modulith.starter.test)

    implementation(libs.antlr.runtime)
    antlr(libs.antlr.tool)
    testImplementation(platform(libs.spring.boot.bom))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}

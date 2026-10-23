plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("internalModuleBoundaries") {
            id = "io.taskmigo.internal-module-boundaries"
            implementationClass = "io.taskmigo.buildlogic.InternalModuleBoundariesPlugin"
        }
    }
}

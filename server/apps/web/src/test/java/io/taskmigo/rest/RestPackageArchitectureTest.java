package io.taskmigo.rest;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RestPackageArchitectureTest {

    @Test
    @DisplayName("keeps REST API and internal package boundaries")
    void shouldKeepRestAndInternalBoundariesWhenPackagesAreInspected() {
        /*
         * Given the web application's REST and internal classes, this verifies that
         * public API code cannot depend on web internals and that feature packages
         * remain independent, preserving the version-first feature layout.
         */
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo");

        ArchRule apiDoesNotDependOnInternal = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.rest.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.internal..");
        ArchRule sharedRestSupportDoesNotDependOnVersionedCode = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.rest.support..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.rest.api.v0..");
        ArchRule authorizationDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.rest.api.v0.auth.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.rest.api.v0.auth.group..", "io.taskmigo.rest.api.v0.auth.user..");
        ArchRule groupDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.rest.api.v0.auth.group..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.rest.api.v0.auth.authorization..", "io.taskmigo.rest.api.v0.auth.user..");
        ArchRule userDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.rest.api.v0.auth.user..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.rest.api.v0.auth.authorization..", "io.taskmigo.rest.api.v0.auth.group..");

        // Act
        apiDoesNotDependOnInternal.check(classes);
        sharedRestSupportDoesNotDependOnVersionedCode.check(classes);
        authorizationDoesNotDependOnOtherFeatures.check(classes);
        groupDoesNotDependOnOtherFeatures.check(classes);

        // Assert
        userDoesNotDependOnOtherFeatures.check(classes);
    }
}

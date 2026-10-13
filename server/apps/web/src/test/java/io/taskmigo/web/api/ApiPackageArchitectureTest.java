package io.taskmigo.web.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApiPackageArchitectureTest {

    /**
     * Verifies API transport packages preserve version and feature isolation.
     *
     * Given: production classes from the web application.
     * Expect: versioned APIs stay independent from web internals and sibling feature packages.
     */
    @Test
    @DisplayName("keeps API and internal package boundaries")
    void shouldKeepApiAndInternalBoundariesWhenPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo");

        ArchRule apiDoesNotDependOnInternal = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.internal..");
        ArchRule sharedApiSupportDoesNotDependOnVersionedCode = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.api.support..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.web.api.v0..");
        ArchRule authorizationDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.api.v0.auth.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.web.api.v0.auth.group..", "io.taskmigo.web.api.v0.auth.user..");
        ArchRule groupDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.api.v0.auth.group..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.web.api.v0.auth.authorization..", "io.taskmigo.web.api.v0.auth.user..");
        ArchRule userDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.api.v0.auth.user..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.web.api.v0.auth.authorization..", "io.taskmigo.web.api.v0.auth.group..");
        ArchRule apiDoesNotOwnTransactions = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.transaction..");

        // Act
        apiDoesNotDependOnInternal.check(classes);
        sharedApiSupportDoesNotDependOnVersionedCode.check(classes);
        authorizationDoesNotDependOnOtherFeatures.check(classes);
        groupDoesNotDependOnOtherFeatures.check(classes);
        userDoesNotDependOnOtherFeatures.check(classes);

        // Assert
        apiDoesNotOwnTransactions.check(classes);
    }
}

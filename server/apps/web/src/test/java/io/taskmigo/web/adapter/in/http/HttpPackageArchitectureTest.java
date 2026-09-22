package io.taskmigo.web.adapter.in.http;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HttpPackageArchitectureTest {

    /**
     * Verifies HTTP transport packages preserve version and feature isolation.
     *
     * Given: production classes from the web application.
     * Expect: versioned HTTP APIs stay independent from web internals and sibling feature packages.
     */
    @Test
    @DisplayName("keeps HTTP API and internal package boundaries")
    void shouldKeepHttpAndInternalBoundariesWhenPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo");

        ArchRule apiDoesNotDependOnInternal = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.internal..");
        ArchRule sharedRestSupportDoesNotDependOnVersionedCode = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.support..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.api.v0..");
        ArchRule authorizationDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.api.v0.auth.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.web.adapter.in.http.api.v0.auth.group..",
                "io.taskmigo.web.adapter.in.http.api.v0.auth.user.."
            );
        ArchRule groupDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.api.v0.auth.group..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.web.adapter.in.http.api.v0.auth.authorization..",
                "io.taskmigo.web.adapter.in.http.api.v0.auth.user.."
            );
        ArchRule userDoesNotDependOnOtherFeatures = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.api.v0.auth.user..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.web.adapter.in.http.api.v0.auth.authorization..",
                "io.taskmigo.web.adapter.in.http.api.v0.auth.group.."
            );
        ArchRule restApiDoesNotOwnTransactions = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.web.adapter.in.http.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.transaction..");

        // Act
        apiDoesNotDependOnInternal.check(classes);
        sharedRestSupportDoesNotDependOnVersionedCode.check(classes);
        authorizationDoesNotDependOnOtherFeatures.check(classes);
        groupDoesNotDependOnOtherFeatures.check(classes);
        userDoesNotDependOnOtherFeatures.check(classes);

        // Assert
        restApiDoesNotOwnTransactions.check(classes);
    }
}

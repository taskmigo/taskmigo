package io.taskmigo.foundation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FoundationPackageArchitectureTest {

    /**
     * Verifies that Foundation stays a minimal framework-neutral dependency floor.
     *
     * Given: every production class in Foundation.
     * Expect: Foundation does not depend on bounded contexts, supporting capabilities, executable applications, or
     * application/persistence frameworks.
     */
    @Test
    @DisplayName("keeps Foundation framework neutral and capability independent")
    void shouldKeepFoundationIndependentWhenHigherLevelCapabilitiesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.foundation");
        ArchRule foundationDoesNotDependOnHigherLevelCapabilities = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.foundation..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..",
                "io.taskmigo.identity..",
                "io.taskmigo.query..",
                "io.taskmigo.language..",
                "io.taskmigo.database..",
                "io.taskmigo.web..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker..",
                "org.springframework..",
                "jakarta.persistence..",
                "jakarta.servlet..",
                "com.fasterxml.jackson..",
                "tools.jackson.."
            );

        // Act + Assert
        foundationDoesNotDependOnHigherLevelCapabilities.check(classes);
    }
}

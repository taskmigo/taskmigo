package io.taskmigo.foundation.jackson;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FoundationJacksonPackageArchitectureTest {

    /**
     * Verifies that the shared Jackson policy remains independent of higher-level Taskmigo capabilities.
     *
     * Given: every production class in Foundation Jackson.
     * Expect: the module depends only on serialization/framework APIs, never bounded contexts or executable apps.
     */
    @Test
    @DisplayName("keeps Foundation Jackson independent of higher-level Taskmigo capabilities")
    void shouldKeepFoundationJacksonIndependentWhenHigherLevelCapabilitiesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.foundation.jackson");
        ArchRule foundationJacksonDoesNotDependOnHigherLevelCapabilities = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.foundation.jackson..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..",
                "io.taskmigo.audit..",
                "io.taskmigo.database..",
                "io.taskmigo.identity..",
                "io.taskmigo.query..",
                "io.taskmigo.web..",
                "io.taskmigo.worker..",
                "io.taskmigo.migration.."
            );

        // Act + Assert
        foundationJacksonDoesNotDependOnHigherLevelCapabilities.check(classes);
    }
}

package io.taskmigo.database;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabasePackageArchitectureTest {

    /**
     * Verifies that shared database infrastructure remains independent from higher-level capabilities.
     *
     * Given: every class provided by the database module.
     * Expect: no database class imports bounded-context, supporting-capability, or executable-application types, so generic
     * Criteria mechanics cannot acquire resource or use-case ownership.
     */
    @Test
    @DisplayName("keeps shared database infrastructure independent from higher-level capabilities")
    void shouldKeepDatabaseInfrastructureIndependentWhenHigherLevelCapabilitiesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.database");
        ArchRule databaseDoesNotDependOnHigherLevelCapabilities = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.database..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..",
                "io.taskmigo.identity..",
                "io.taskmigo.query..",
                "io.taskmigo.language..",
                "io.taskmigo.web..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker.."
            );

        // Act + Assert
        databaseDoesNotDependOnHigherLevelCapabilities.check(classes);
    }
}

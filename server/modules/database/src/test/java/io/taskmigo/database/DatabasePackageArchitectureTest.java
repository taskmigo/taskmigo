package io.taskmigo.database;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatabasePackageArchitectureTest {

    /**
     * Verifies that shared database infrastructure remains independent from bounded-context code.
     *
     * Given: every class provided by the database module.
     * Expect: no database class imports Access Control or Identity types, so generic Criteria
     * mechanics cannot acquire resource ownership.
     */
    @Test
    @DisplayName("keeps shared database infrastructure independent from bounded contexts")
    void shouldKeepDatabaseInfrastructureIndependentWhenDatabasePackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.database");
        ArchRule databaseDoesNotDependOnBoundedContexts = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.database..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.authorization..", "io.taskmigo.identity..");

        // Act + Assert
        databaseDoesNotDependOnBoundedContexts.check(classes);
    }
}

package io.taskmigo.jpaquery;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaQueryPackageArchitectureTest {

    /**
     * Verifies that reusable JPA query mechanics are owned entirely by the JPA Query capability.
     *
     * Given: all production classes in JPA Query.
     * Expect: no JPA Query class depends on the Database runtime/configuration module.
     */
    @Test
    @DisplayName("keeps JPA Query independent from Database runtime ownership")
    void shouldKeepJpaQueryIndependentWhenDatabaseRuntimeIsInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.jpaquery");
        ArchRule jpaQueryDoesNotDependOnDatabase = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.jpaquery..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.database..");

        // Act + Assert
        jpaQueryDoesNotDependOnDatabase.check(classes);
    }
}

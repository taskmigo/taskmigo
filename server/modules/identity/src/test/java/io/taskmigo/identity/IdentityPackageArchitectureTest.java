package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityPackageArchitectureTest {

    /**
     * Verifies that persistence internals do not depend on application adapters.
     *
     * Given: all classes in the consolidated Identity capability.
     * Expect: persistence entities, repositories, and binders remain below the application boundary.
     */
    @Test
    @DisplayName("keeps identity persistence below application adapters")
    void shouldKeepPersistenceBelowApplicationsWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = new ClassFileImporter().importPackages("io.taskmigo.identity");
        ArchRule persistenceDoesNotDependOnApplications = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.rest..",
                "io.taskmigo.internal..",
                "io.taskmigo.bootstrap..",
                "io.taskmigo.worker.."
            );

        // Act + Assert
        persistenceDoesNotDependOnApplications.check(classes);
    }
}

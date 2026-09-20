package io.taskmigo.migration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MigrationPackageArchitectureTest {

    /**
     * Verifies that managed-resource reconciliation consumes provider-owned provisioning boundaries.
     *
     * Given: the migration ManagedResourceReconciler production class.
     * Expect: it does not reach into Identity runtime capabilities or Access Control runtime/application/persistence APIs.
     */
    @Test
    @DisplayName("keeps managed-resource reconciliation on provider-owned provisioning boundaries")
    void shouldUseProvisioningBoundariesWhenManagedResourcesAreReconciled() {
        // Arrange
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.migration");
        ArchRule rule = noClasses()
            .that()
            .haveSimpleName("ManagedResourceReconciler")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user..",
                "io.taskmigo.identity.group..",
                "io.taskmigo.identity.membership..",
                "io.taskmigo.authorization.role..",
                "io.taskmigo.authorization.statement.application..",
                "io.taskmigo.authorization.statement.infrastructure..",
                "io.taskmigo.authorization.subject..",
                "io.taskmigo.authorization.request..",
                "io.taskmigo.authorization.object..",
                "io.taskmigo.authorization.persistence.."
            );

        // Act + Assert
        rule.check(classes);
    }
}

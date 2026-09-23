package io.taskmigo.migration.adapter.in.installation;

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
        JavaClasses classes = productionClasses();
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
                "io.taskmigo.authorization.subject..",
                "io.taskmigo.authorization.request..",
                "io.taskmigo.authorization.object..",
                "io.taskmigo.authorization..adapter.."
            );

        // Act + Assert
        rule.check(classes);
    }

    /**
     * Verifies that the installation driving adapter cannot reach driven or application implementation details.
     *
     * Given: production classes under migration adapter.in.
     * Expect: they depend on the inbound port/model plus input-framework APIs, not outbound ports/adapters or persistence
     * transaction/security mechanics.
     */
    @Test
    @DisplayName("keeps migration driving adapters independent from driven infrastructure")
    void shouldKeepDrivingAdaptersIndependentWhenMigrationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.migration..adapter.in..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.migration..application.service..",
                "io.taskmigo.migration..application.port.out..",
                "io.taskmigo.migration..adapter.out..",
                "org.springframework.jdbc..",
                "org.springframework.transaction..",
                "org.springframework.security.crypto..",
                "org.springframework.security.oauth2.server.authorization.client.."
            );

        // Act + Assert
        rule.check(classes);
    }

    /**
     * Verifies that migration application orchestration remains framework neutral.
     *
     * Given: production classes under migration application packages.
     * Expect: they do not depend on migration adapters, Spring, JPA, or serialization frameworks.
     */
    @Test
    @DisplayName("keeps migration application core framework neutral")
    void shouldKeepApplicationCoreFrameworkNeutralWhenMigrationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.migration..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.migration..adapter..",
                "org.springframework..",
                "jakarta.persistence..",
                "tools.jackson.."
            );

        // Act + Assert
        rule.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.migration");
    }
}

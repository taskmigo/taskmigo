package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.taskmigo.architecture.OnionArchitectureRules;
import io.taskmigo.architecture.OnionArchitectureRules.Context;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityPackageArchitectureTest {

    private static final Context ONION_CONTEXT = new Context(
        "io.taskmigo.identity",
        List.of("io.taskmigo.identity..application.."),
        List.of("io.taskmigo.identity..persistence.."),
        List.of(
            "io.taskmigo.identity.user.configuration..",
            "io.taskmigo.identity.group.configuration..",
            "io.taskmigo.identity.membership.configuration..",
            "io.taskmigo.identity.provisioning.configuration..",
            "io.taskmigo.identity.configuration.configuration.."
        ),
        List.of("io.taskmigo.identity..integration.."),
        List.of("io.taskmigo.identity..integration..")
    );

    /**
     * Verifies the completed Identity migration cannot regress to mandatory Hexagonal package vocabulary.
     *
     * Given: all production classes in the Identity bounded context.
     * Expect: no class resides in the retired adapter, port, service-layer, composition, or infrastructure packages.
     */
    @Test
    @DisplayName("rejects retired Identity architecture package names")
    void shouldRejectRetiredArchitecturePackagesWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule noRetiredPackages = noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.identity.adapter..",
                "io.taskmigo.identity.application.port..",
                "io.taskmigo.identity..application.service..",
                "io.taskmigo.identity..composition..",
                "io.taskmigo.identity..infrastructure.."
            );

        // Act + Assert
        noRetiredPackages.check(classes);
    }

    /**
     * Verifies the removed Identity transaction-runner architecture cannot return unnoticed.
     *
     * Given: all production classes in Identity after declarative transaction migration.
     * Expect: neither the legacy runner type nor its transaction implementation package exists.
     */
    @Test
    @DisplayName("rejects the legacy Identity transaction runner")
    void shouldRejectLegacyTransactionRunnerWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();

        // Act + Assert
        noClasses()
            .should()
            .haveFullyQualifiedName("io.taskmigo.identity.application.TransactionRunner")
            .check(classes);
        noClasses().should().resideInAnyPackage("io.taskmigo.identity.transaction..").check(classes);
    }

    /**
     * Verifies Identity follows the repository-wide responsibility-oriented Onion baseline.
     *
     * Given: the completed Identity Phase 1 package structure.
     * Expect: domain/application dependencies point inward, persistence frameworks stay in persistence packages, and
     * Identity never depends back on executable applications.
     */
    @Test
    @DisplayName("enforces the shared Onion dependency baseline for Identity")
    void shouldEnforceSharedOnionRulesWhenIdentityPackagesAreInspected() {
        OnionArchitectureRules.checkProduction(ONION_CONTEXT);
    }

    /**
     * Verifies that Identity integrates with Access Control only through published contracts.
     *
     * Given: all production classes in Identity.
     * Expect: no Identity class reaches into Access Control persistence or adapter implementation packages.
     */
    @Test
    @DisplayName("keeps Access Control persistence private from Identity")
    void shouldKeepAccessControlPersistencePrivateWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule identityDoesNotDependOnAccessControlPersistence = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.authorization..adapter..", "io.taskmigo.authorization.persistence..");

        // Act + Assert
        identityDoesNotDependOnAccessControlPersistence.check(classes);
    }

    /**
     * Verifies published root contracts remain independent from their feature implementations.
     *
     * Given: the User, Group, Membership, Configuration, and Provisioning public contract packages.
     * Expect: contracts do not depend on application implementations, persistence, integrations, or Spring wiring.
     */
    @Test
    @DisplayName("keeps published Identity contracts independent from implementation layers")
    void shouldKeepPublishedContractsIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsDoNotDependOnImplementations = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.user",
                "io.taskmigo.identity.group",
                "io.taskmigo.identity.membership",
                "io.taskmigo.identity.configuration",
                "io.taskmigo.identity.provisioning"
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..application..",
                "io.taskmigo.identity..persistence..",
                "io.taskmigo.identity..integration..",
                "io.taskmigo.identity..configuration.configuration.."
            );

        // Act + Assert
        contractsDoNotDependOnImplementations.check(classes);
    }

    /**
     * Verifies published Identity contracts remain independent from transport and OAuth server implementation details.
     *
     * Given: public Identity contract packages.
     * Expect: those contracts do not depend on Jackson, Spring Web, Servlet, or Spring Authorization Server APIs.
     */
    @Test
    @DisplayName("keeps Identity contracts framework neutral")
    void shouldKeepContractsFrameworkNeutralWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsDoNotDependOnFrameworkDetails = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.provisioning..",
                "io.taskmigo.identity.user",
                "io.taskmigo.identity.group",
                "io.taskmigo.identity.membership",
                "io.taskmigo.identity.configuration",
                "io.taskmigo.identity.authorization.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "com.fasterxml.jackson..",
                "tools.jackson..",
                "org.springframework.web..",
                "jakarta.servlet..",
                "org.springframework.security.oauth2.server.authorization.."
            );

        // Act + Assert
        contractsDoNotDependOnFrameworkDetails.check(classes);
    }

    /**
     * Verifies that Group hierarchy rules remain independent from persistence implementation details.
     *
     * Given: production classes in the Group hierarchy domain package.
     * Expect: hierarchy rules do not depend on Identity persistence or JPA types.
     */
    @Test
    @DisplayName("keeps Group hierarchy independent from persistence")
    void shouldKeepGroupHierarchyIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule hierarchyDoesNotDependOnPersistence = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.group.domain.hierarchy..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..persistence..",
                "jakarta.persistence..",
                "org.springframework.data.."
            );

        // Act + Assert
        hierarchyDoesNotDependOnPersistence.check(classes);
    }

    /**
     * Verifies that Identity no longer imports Access Control Role models or broad Role services.
     *
     * Given: every production class owned by Identity after the contract split.
     * Expect: Identity depends on subject capabilities and subject-resolution contracts, not the Role package.
     */
    @Test
    @DisplayName("keeps Access Control Role contracts out of Identity")
    void shouldAvoidRoleContractsWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule identityDoesNotDependOnRoleContracts = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.authorization.role..");

        // Act + Assert
        identityDoesNotDependOnRoleContracts.check(classes);
    }

    /// Verifies that User persistence cleanup uses repository APIs rather than application SQL.
    ///
    /// Given: production User persistence classes, including OAuth cleanup.
    /// Expect: no persistence class depends on Spring JDBC execution APIs.
    @Test
    @DisplayName("keeps application SQL out of User persistence")
    void shouldUseRepositoryApisWhenUserPersistenceIsInspected() {
        // Arrange
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.jdbc.core..");

        // Act + Assert
        rule.check(productionClasses());
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity");
    }
}

package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityPackageArchitectureTest {

    /**
     * Verifies that persistence internals do not depend on application adapters.
     *
     * Given: all production classes in the consolidated Identity capability.
     * Expect: persistence entities, repositories, and binders remain below the application boundary.
     */
    @Test
    @DisplayName("keeps identity persistence below application adapters")
    void shouldKeepPersistenceBelowApplicationsWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule persistenceDoesNotDependOnApplications = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.rest..",
                "io.taskmigo.internal..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker.."
            );

        // Act + Assert
        persistenceDoesNotDependOnApplications.check(classes);
    }

    /**
     * Verifies that Identity integrates with Access Control only through published contracts.
     *
     * Given: all production classes in the Identity bounded context.
     * Expect: no Identity class reaches into Access Control persistence internals.
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
            .resideInAnyPackage("io.taskmigo.authorization.persistence..");

        // Act + Assert
        identityDoesNotDependOnAccessControlPersistence.check(classes);
    }

    /**
     * Verifies that Identity domain/application packages do not reach into JPA adapters.
     *
     * Given: production User domain/application, Group, provisioning, and Identity authorization packages.
     * Expect: their dependencies exclude Identity persistence, Spring Data, and JPA packages.
     */
    @Test
    @DisplayName("keeps Identity use cases independent from JPA adapters")
    void shouldKeepUseCasesIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule useCasesDoNotDependOnJpaAdapters = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.provisioning..",
                "io.taskmigo.identity.user.domain..",
                "io.taskmigo.identity.user.application..",
                "io.taskmigo.identity.group.domain..",
                "io.taskmigo.identity.group.application..",
                "io.taskmigo.identity.membership.application..",
                "io.taskmigo.identity.authorization.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.persistence..",
                "io.taskmigo.identity.user.infrastructure..",
                "io.taskmigo.identity.group.infrastructure..",
                "io.taskmigo.identity.membership.infrastructure..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        useCasesDoNotDependOnJpaAdapters.check(classes);
    }

    /**
     * Verifies that published Identity contracts remain independent from framework-specific representation and OAuth
     * server implementation concerns.
     *
     * Given: production provisioning, User, Group, and Identity authorization contract packages.
     * Expect: those classes do not depend on Spring Core type descriptors, Jackson 2/3, Spring Web, Servlet, or Spring
     * Authorization Server implementation APIs.
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
                "io.taskmigo.identity.user..",
                "io.taskmigo.identity.group",
                "io.taskmigo.identity.membership",
                "io.taskmigo.identity.authorization.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework.core..",
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
     * Verifies the published User package cannot reach outward into application or infrastructure implementation.
     *
     * Given: production classes in the root User API package.
     * Expect: published User contracts may depend on domain concepts but not application services or infrastructure.
     */
    @Test
    @DisplayName("keeps User API independent from implementation layers")
    void shouldKeepUserApiIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule userApiDoesNotDependOnImplementation = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.application..",
                "io.taskmigo.identity.user.infrastructure.."
            );

        // Act + Assert
        userApiDoesNotDependOnImplementation.check(classes);
    }

    /**
     * Verifies the User slice's domain dependency direction.
     *
     * Given: the canonical User domain package.
     * Expect: domain code has no dependency on application/infrastructure packages, Spring, Spring Data, or JPA.
     */
    @Test
    @DisplayName("keeps User domain framework neutral and inward")
    void shouldKeepUserDomainIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule userDomainDependsOnlyInward = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.application..",
                "io.taskmigo.identity.user.infrastructure..",
                "org.springframework..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        userDomainDependsOnlyInward.check(classes);
    }

    /**
     * Verifies the User application layer does not reach into its JPA adapter.
     *
     * Given: User application services and command/query ports.
     * Expect: they do not depend on User infrastructure, Spring Data, or JPA.
     */
    @Test
    @DisplayName("keeps User application independent from infrastructure")
    void shouldKeepUserApplicationIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule userApplicationDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.infrastructure..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        userApplicationDoesNotDependOnInfrastructure.check(classes);
    }

    /**
     * Verifies the published Group and Membership APIs remain independent from implementation layers.
     *
     * Given: root API packages for Group and Membership.
     * Expect: published contracts do not depend on their application or infrastructure implementations.
     */
    @Test
    @DisplayName("keeps Group and Membership APIs independent from implementation layers")
    void shouldKeepGroupAndMembershipApisIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule apiDoesNotDependOnImplementation = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.group", "io.taskmigo.identity.membership")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.application..",
                "io.taskmigo.identity.group.infrastructure..",
                "io.taskmigo.identity.membership.application..",
                "io.taskmigo.identity.membership.infrastructure.."
            );

        // Act + Assert
        apiDoesNotDependOnImplementation.check(classes);
    }

    /**
     * Verifies Group domain and application dependency direction.
     *
     * Given: the canonical Group domain and application packages.
     * Expect: domain is framework neutral and application does not depend on Group/Membership infrastructure.
     */
    @Test
    @DisplayName("keeps Group domain and application independent from infrastructure")
    void shouldKeepGroupLayersIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule groupDomainDoesNotDependOutward = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.group.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.application..",
                "io.taskmigo.identity.group.infrastructure..",
                "org.springframework..",
                "jakarta.persistence.."
            );
        ArchRule groupApplicationDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.application..",
                "io.taskmigo.identity.membership.application.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.infrastructure..",
                "io.taskmigo.identity.membership.infrastructure..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        groupDomainDoesNotDependOutward.check(classes);
        groupApplicationDoesNotDependOnInfrastructure.check(classes);
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
                "io.taskmigo.identity.persistence..",
                "jakarta.persistence..",
                "org.springframework.data.."
            );

        // Act + Assert
        hierarchyDoesNotDependOnPersistence.check(classes);
    }

    /**
     * Verifies that Identity no longer imports Access Control Role models or broad Role services.
     *
     * Given: every production class owned by Identity after the Phase 6 contract split.
     * Expect: Identity depends on subject capabilities and the effective-subject SPI, not the Role package.
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

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity");
    }
}

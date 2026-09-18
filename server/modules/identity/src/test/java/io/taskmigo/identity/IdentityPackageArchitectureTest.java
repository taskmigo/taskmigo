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
                "io.taskmigo.bootstrap..",
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
     * Verifies that Identity domain and application packages do not reach into JPA adapters.
     *
     * Given: production User, Group, and Identity authorization packages.
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
                "io.taskmigo.identity.user..",
                "io.taskmigo.identity.group..",
                "io.taskmigo.identity.authorization.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.persistence..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        useCasesDoNotDependOnJpaAdapters.check(classes);
    }

    /**
     * Verifies that published Identity contracts remain independent from HTTP serialization concerns.
     *
     * Given: production User and Group contract packages.
     * Expect: those classes do not depend on Jackson, Spring Web, or Servlet APIs.
     */
    @Test
    @DisplayName("keeps Identity contracts transport neutral")
    void shouldKeepContractsTransportNeutralWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsDoNotDependOnTransport = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.identity.provisioning..",
                "io.taskmigo.identity.user..",
                "io.taskmigo.identity.group.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("com.fasterxml.jackson..", "org.springframework.web..", "jakarta.servlet..");

        // Act + Assert
        contractsDoNotDependOnTransport.check(classes);
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
            .resideInAnyPackage("io.taskmigo.identity.group.hierarchy..")
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

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity");
    }
}

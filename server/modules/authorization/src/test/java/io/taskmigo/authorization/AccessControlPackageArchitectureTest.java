package io.taskmigo.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessControlPackageArchitectureTest {

    /**
     * Verifies the bounded-context dependency direction between Access Control and Identity.
     *
     * Given: all production classes owned by Access Control.
     * Expect: Access Control remains independent from Identity resources and persistence.
     */
    @Test
    @DisplayName("keeps Access Control independent from Identity")
    void shouldKeepAccessControlIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule accessControlDoesNotDependOnIdentity = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.identity..");

        // Act + Assert
        accessControlDoesNotDependOnIdentity.check(classes);
    }

    /**
     * Verifies that Access Control domain and application contracts do not reach into persistence adapters.
     *
     * Given: every production package that owns Access Control domain, application, or published SPI contracts.
     * Expect: their dependencies exclude Access Control persistence, Spring Data, and JPA packages.
     */
    @Test
    @DisplayName("keeps Access Control contracts independent from persistence adapters")
    void shouldKeepContractsIndependentWhenAccessControlPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsDoNotDependOnJpaAdapters = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization.core..",
                "io.taskmigo.authorization.object..",
                "io.taskmigo.authorization.provisioning..",
                "io.taskmigo.authorization.request..",
                "io.taskmigo.authorization.role",
                "io.taskmigo.authorization.role.application..",
                "io.taskmigo.authorization.role.domain..",
                "io.taskmigo.authorization.spi..",
                "io.taskmigo.authorization.statement",
                "io.taskmigo.authorization.statement.application..",
                "io.taskmigo.authorization.statement.domain..",
                "io.taskmigo.authorization.subject.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.persistence..",
                "io.taskmigo.authorization.role.infrastructure..",
                "io.taskmigo.authorization.statement.infrastructure..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        contractsDoNotDependOnJpaAdapters.check(classes);
    }

    /**
     * Verifies published Access Control contracts remain independent from framework-specific representation concerns.
     *
     * Given: production classes in the public/domain/application Access Control contract packages.
     * Expect: those classes do not depend on web, serialization, or OAuth-server implementation APIs.
     */
    @Test
    @DisplayName("keeps Access Control contracts framework neutral")
    void shouldKeepContractsFrameworkNeutralWhenAccessControlPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsDoNotDependOnFrameworkDetails = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization.core..",
                "io.taskmigo.authorization.object..",
                "io.taskmigo.authorization.provisioning..",
                "io.taskmigo.authorization.request..",
                "io.taskmigo.authorization.role",
                "io.taskmigo.authorization.role.application..",
                "io.taskmigo.authorization.role.domain..",
                "io.taskmigo.authorization.spi..",
                "io.taskmigo.authorization.statement",
                "io.taskmigo.authorization.statement.application..",
                "io.taskmigo.authorization.statement.domain..",
                "io.taskmigo.authorization.subject.."
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
     * Verifies the published Statement package cannot reach into application or infrastructure implementation.
     *
     * Given: production classes in the root Statement API package.
     * Expect: published Statement contracts remain independent from application services and JPA adapters.
     */
    @Test
    @DisplayName("keeps Statement API independent from implementation layers")
    void shouldKeepStatementApiIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule apiDoesNotDependOnImplementation = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.statement")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.statement.application..",
                "io.taskmigo.authorization.statement.infrastructure.."
            );

        // Act + Assert
        apiDoesNotDependOnImplementation.check(classes);
    }

    /**
     * Verifies Statement domain and application dependency direction.
     *
     * Given: the canonical Statement domain and application packages.
     * Expect: domain is framework-neutral and application does not depend on Statement infrastructure.
     */
    @Test
    @DisplayName("keeps Statement domain and application independent from infrastructure")
    void shouldKeepStatementLayersIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule domainDoesNotDependOutward = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.statement.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.statement.application..",
                "io.taskmigo.authorization.statement.infrastructure..",
                "org.springframework..",
                "jakarta.persistence.."
            );
        ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.statement.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.statement.infrastructure..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        domainDoesNotDependOutward.check(classes);
        applicationDoesNotDependOnInfrastructure.check(classes);
    }

    /**
     * Verifies the published Role package cannot reach into tactical implementation layers.
     *
     * Given: production classes in the root Role API package.
     * Expect: published Role contracts remain independent from application services and JPA adapters.
     */
    @Test
    @DisplayName("keeps Role API independent from implementation layers")
    void shouldKeepRoleApiIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule apiDoesNotDependOnImplementation = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.role")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.role.application..",
                "io.taskmigo.authorization.role.infrastructure.."
            );

        // Act + Assert
        apiDoesNotDependOnImplementation.check(classes);
    }

    /**
     * Verifies Role domain and application dependency direction.
     *
     * Given: canonical Role domain and application packages.
     * Expect: Role domain is framework-neutral and Role application code does not depend on Role infrastructure.
     */
    @Test
    @DisplayName("keeps Role domain and application independent from infrastructure")
    void shouldKeepRoleLayersIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule domainDoesNotDependOutward = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.role.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.role.application..",
                "io.taskmigo.authorization.role.infrastructure..",
                "org.springframework..",
                "jakarta.persistence.."
            );
        ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.role.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.role.infrastructure..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        domainDoesNotDependOutward.check(classes);
        applicationDoesNotDependOnInfrastructure.check(classes);
    }

    /**
     * Verifies direct Subject grant domain and application dependency direction.
     *
     * Given: the Phase 6 Subject grant domain and application packages.
     * Expect: domain stays framework-neutral and application stays independent from persistence adapters.
     */
    @Test
    @DisplayName("keeps Subject grant domain and application independent from infrastructure")
    void shouldKeepSubjectGrantLayersIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule domainDoesNotDependOutward = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.subject.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.subject.application..",
                "io.taskmigo.authorization.persistence..",
                "org.springframework..",
                "jakarta.persistence.."
            );
        ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization.subject.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.persistence..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        domainDoesNotDependOutward.check(classes);
        applicationDoesNotDependOnInfrastructure.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization");
    }
}

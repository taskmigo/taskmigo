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
                "io.taskmigo.authorization.role..",
                "io.taskmigo.authorization.spi..",
                "io.taskmigo.authorization.statement..",
                "io.taskmigo.authorization.subject.."
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization.persistence..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        contractsDoNotDependOnJpaAdapters.check(classes);
    }

    /**
     * Verifies that published Access Control contracts remain independent from framework-specific representation and
     * OAuth server implementation concerns.
     *
     * Given: production classes in the public Access Control contract packages.
     * Expect: those classes do not depend on Spring Core type descriptors, Jackson 2/3, Spring Web, Servlet, or Spring
     * Authorization Server implementation APIs.
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
                "io.taskmigo.authorization.role..",
                "io.taskmigo.authorization.spi..",
                "io.taskmigo.authorization.statement..",
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

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization");
    }
}

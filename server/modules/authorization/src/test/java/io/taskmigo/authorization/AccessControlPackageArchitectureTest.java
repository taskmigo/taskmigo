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
     * Verifies that Access Control domain and application packages do not reach into persistence adapters.
     *
     * Given: production Role, Statement, and subject application/domain packages.
     * Expect: their dependencies exclude Access Control persistence, Spring Data, and JPA packages.
     */
    @Test
    @DisplayName("keeps Access Control use cases independent from JPA adapters")
    void shouldKeepUseCasesIndependentWhenAccessControlPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule useCasesDoNotDependOnJpaAdapters = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization.provisioning..",
                "io.taskmigo.authorization.role..",
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
        useCasesDoNotDependOnJpaAdapters.check(classes);
    }

    /**
     * Verifies that published Access Control contracts remain independent from HTTP serialization concerns.
     *
     * Given: production classes in the public Access Control contract packages.
     * Expect: those classes do not depend on Jackson, Spring Web, or Servlet APIs.
     */
    @Test
    @DisplayName("keeps Access Control contracts transport neutral")
    void shouldKeepContractsTransportNeutralWhenAccessControlPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule contractsDoNotDependOnTransport = noClasses()
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
            .resideInAnyPackage("com.fasterxml.jackson..", "org.springframework.web..", "jakarta.servlet..");

        // Act + Assert
        contractsDoNotDependOnTransport.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization");
    }
}

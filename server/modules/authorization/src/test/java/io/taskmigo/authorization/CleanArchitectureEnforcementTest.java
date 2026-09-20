package io.taskmigo.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CleanArchitectureEnforcementTest {

    /**
     * Verifies that every Access Control domain package stays inward-facing and framework-neutral.
     *
     * Given: all production classes below an Access Control domain package.
     * Expect: domain code has no dependency on application/infrastructure layers, application adapters, Spring, or JPA.
     */
    @Test
    @DisplayName("keeps all Access Control domain packages inward and framework neutral")
    void shouldKeepDomainInwardWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..application..",
                "io.taskmigo.authorization..infrastructure..",
                "io.taskmigo.authorization.persistence..",
                "io.taskmigo.authorization.object.persistence..",
                "io.taskmigo.rest..",
                "io.taskmigo.internal..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker..",
                "org.springframework..",
                "jakarta.persistence.."
            );

        // Act + Assert
        rule.check(classes);
    }

    /**
     * Verifies that Access Control application orchestration stays independent from outward adapters.
     *
     * Given: application packages plus request/object orchestration in their root capability packages.
     * Expect: application code depends on inward ports rather than persistence, web/migration/worker adapters, Spring Data, or JPA.
     */
    @Test
    @DisplayName("keeps all Access Control application orchestration independent from outward adapters")
    void shouldKeepApplicationIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization..application..",
                "io.taskmigo.authorization.request",
                "io.taskmigo.authorization.object"
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..infrastructure..",
                "io.taskmigo.authorization.persistence..",
                "io.taskmigo.rest..",
                "io.taskmigo.internal..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        rule.check(classes);
    }

    /**
     * Verifies that published Access Control contracts cannot expose tactical implementation types.
     *
     * Given: public classes in Access Control named-interface packages.
     * Expect: their dependencies exclude application/infrastructure implementation, application adapters, Spring Data, and JPA.
     */
    @Test
    @DisplayName("keeps published Access Control APIs independent from implementation types")
    void shouldKeepPublishedApisIndependentWhenAuthorizationPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .arePublic()
            .and()
            .resideInAnyPackage(
                "io.taskmigo.authorization.core",
                "io.taskmigo.authorization.object",
                "io.taskmigo.authorization.provisioning",
                "io.taskmigo.authorization.request",
                "io.taskmigo.authorization.role",
                "io.taskmigo.authorization.spi",
                "io.taskmigo.authorization.statement",
                "io.taskmigo.authorization.subject"
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..application..",
                "io.taskmigo.authorization..infrastructure..",
                "io.taskmigo.authorization.persistence..",
                "io.taskmigo.rest..",
                "io.taskmigo.internal..",
                "io.taskmigo.migration..",
                "io.taskmigo.worker..",
                "org.springframework.data..",
                "jakarta.persistence.."
            );

        // Act + Assert
        rule.check(classes);
    }

    /**
     * Verifies that tactical unit tests stay detached from Spring and persistence runtimes.
     *
     * Given: Access Control domain/application tests plus request/object orchestration tests.
     * Expect: domain tests do not use Spring, and application tests do not use persistence adapters or Spring test contexts.
     */
    @Test
    @DisplayName("keeps Access Control domain and application tests lightweight")
    void shouldKeepTacticalTestsLightweightWhenAuthorizationTestsAreInspected() {
        // Arrange
        JavaClasses classes = allClasses();
        ArchRule domainTestsWithoutSpring = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.authorization..domain..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");
        ArchRule applicationTestsWithoutPersistenceRuntime = noClasses()
            .that()
            .resideInAnyPackage(
                "io.taskmigo.authorization..application..",
                "io.taskmigo.authorization.request",
                "io.taskmigo.authorization.object"
            )
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.authorization..infrastructure..",
                "io.taskmigo.authorization.persistence..",
                "org.springframework.data..",
                "jakarta.persistence..",
                "org.springframework.boot.test.context..",
                "org.springframework.test.context.."
            );

        // Act + Assert
        domainTestsWithoutSpring.check(classes);
        applicationTestsWithoutPersistenceRuntime.check(classes);
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization");
    }

    private static JavaClasses allClasses() {
        return new ClassFileImporter().importPackages("io.taskmigo.authorization");
    }
}

package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CleanArchitectureEnforcementTest {

    /**
     * Verifies that every Identity domain package stays inward-facing and framework-neutral.
     *
     * Given: all production classes below an Identity domain package.
     * Expect: domain code has no dependency on application/infrastructure layers, application adapters, Spring, or JPA.
     */
    @Test
    @DisplayName("keeps all Identity domain packages inward and framework neutral")
    void shouldKeepDomainInwardWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..application..",
                "io.taskmigo.identity..infrastructure..",
                "io.taskmigo.identity.persistence..",
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
     * Verifies that Identity application orchestration stays independent from outward adapters.
     *
     * Given: all production classes below an Identity application package, including provisioning application services.
     * Expect: application code depends on ports rather than infrastructure, web/migration/worker adapters, Spring Data, or JPA.
     */
    @Test
    @DisplayName("keeps all Identity application packages independent from outward adapters")
    void shouldKeepApplicationIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..infrastructure..",
                "io.taskmigo.identity.persistence..",
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
     * Verifies that published Identity contracts cannot expose tactical implementation types.
     *
     * Given: public classes in Identity named-interface packages.
     * Expect: their dependencies exclude application/infrastructure implementation, application adapters, Spring Data, and JPA.
     */
    @Test
    @DisplayName("keeps published Identity APIs independent from implementation types")
    void shouldKeepPublishedApisIndependentWhenIdentityPackagesAreInspected() {
        // Arrange
        JavaClasses classes = productionClasses();
        ArchRule rule = noClasses()
            .that()
            .arePublic()
            .and()
            .resideInAnyPackage(
                "io.taskmigo.identity.user",
                "io.taskmigo.identity.group",
                "io.taskmigo.identity.membership",
                "io.taskmigo.identity.provisioning",
                "io.taskmigo.identity.authorization"
            )
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..application..",
                "io.taskmigo.identity..infrastructure..",
                "io.taskmigo.identity.persistence..",
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
     * Given: Identity domain and application test classes.
     * Expect: domain tests do not use Spring, and application tests do not use persistence adapters or Spring test contexts.
     */
    @Test
    @DisplayName("keeps Identity domain and application tests lightweight")
    void shouldKeepTacticalTestsLightweightWhenIdentityTestsAreInspected() {
        // Arrange
        JavaClasses classes = allClasses();
        ArchRule domainTestsWithoutSpring = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..domain..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");
        ArchRule applicationTestsWithoutPersistenceRuntime = noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity..application..")
            .and()
            .haveSimpleNameEndingWith("Test")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity..infrastructure..",
                "io.taskmigo.identity.persistence..",
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
            .importPackages("io.taskmigo.identity");
    }

    private static JavaClasses allClasses() {
        return new ClassFileImporter().importPackages("io.taskmigo.identity");
    }
}

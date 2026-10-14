package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityUserOnionArchitectureTest {

    @Test
    @DisplayName("keeps Identity User free from mandatory Hexagonal package vocabulary")
    void shouldRejectHexagonalPackageVocabularyWhenUserPackagesAreInspected() {
        JavaClasses classes = userProductionClasses();

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.application.port..",
                "io.taskmigo.identity.user.application.service..",
                "io.taskmigo.identity.user.adapter..",
                "io.taskmigo.identity.user.composition.."
            )
            .as("Identity User uses responsibility-oriented Onion packages rather than mandatory Hexagonal vocabulary")
            .check(classes);
    }

    @Test
    @DisplayName("keeps the User domain inward and framework neutral")
    void shouldKeepUserDomainInwardWhenUserPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.application..",
                "io.taskmigo.identity.user.persistence..",
                "io.taskmigo.identity.user.integration..",
                "io.taskmigo.identity.user.configuration..",
                "org.springframework..",
                "jakarta.persistence.."
            )
            .check(userProductionClasses());
    }

    @Test
    @DisplayName("keeps User application code independent from outward implementations")
    void shouldKeepUserApplicationIndependentWhenUserPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.persistence..",
                "io.taskmigo.identity.user.integration..",
                "io.taskmigo.identity.user.configuration..",
                "org.springframework.data..",
                "jakarta.persistence.."
            )
            .check(userProductionClasses());
    }

    @Test
    @DisplayName("keeps published User use cases independent from private application contracts")
    void shouldKeepPublishedUseCasesIndependentWhenUserPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.user.application.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.user.application.internal..",
                "io.taskmigo.identity.user.persistence..",
                "io.taskmigo.identity.user.integration..",
                "io.taskmigo.identity.user.configuration.."
            )
            .check(userProductionClasses());
    }

    @Test
    @DisplayName("keeps Spring Data and JPA usage inside User persistence")
    void shouldKeepPersistenceFrameworksInsideUserPersistenceWhenUserPackagesAreInspected() {
        noClasses()
            .that()
            .resideOutsideOfPackage("io.taskmigo.identity.user.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .check(userProductionClasses());
    }

    private static JavaClasses userProductionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity.user");
    }
}

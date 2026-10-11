package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityGroupOnionArchitectureTest {

    @Test
    @DisplayName("keeps Identity Group free from mandatory Hexagonal package vocabulary")
    void shouldRejectHexagonalPackageVocabularyWhenGroupPackagesAreInspected() {
        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.application.port..",
                "io.taskmigo.identity.group.application.service..",
                "io.taskmigo.identity.group.adapter..",
                "io.taskmigo.identity.group.composition.."
            )
            .as("Identity Group uses responsibility-oriented Onion packages rather than mandatory Hexagonal vocabulary")
            .check(groupProductionClasses());
    }

    @Test
    @DisplayName("keeps the Group domain inward and framework neutral")
    void shouldKeepGroupDomainInwardWhenGroupPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.group.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.application..",
                "io.taskmigo.identity.group.persistence..",
                "io.taskmigo.identity.group.configuration..",
                "org.springframework..",
                "jakarta.persistence.."
            )
            .check(groupProductionClasses());
    }

    @Test
    @DisplayName("keeps Group application code independent from outward implementations")
    void shouldKeepGroupApplicationIndependentWhenGroupPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.group.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.persistence..",
                "io.taskmigo.identity.group.configuration..",
                "org.springframework.data..",
                "jakarta.persistence.."
            )
            .check(groupProductionClasses());
    }

    @Test
    @DisplayName("keeps published Group use cases independent from private application contracts")
    void shouldKeepPublishedUseCasesIndependentWhenGroupPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.group.application.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.group.application.internal..",
                "io.taskmigo.identity.group.persistence..",
                "io.taskmigo.identity.group.configuration.."
            )
            .check(groupProductionClasses());
    }

    @Test
    @DisplayName("keeps Spring Data and JPA usage inside Group persistence")
    void shouldKeepPersistenceFrameworksInsideGroupPersistenceWhenGroupPackagesAreInspected() {
        noClasses()
            .that()
            .resideOutsideOfPackage("io.taskmigo.identity.group.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .check(groupProductionClasses());
    }

    private static JavaClasses groupProductionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity.group");
    }
}

package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityConfigurationOnionArchitectureTest {

    @Test
    @DisplayName("keeps Identity Configuration free from mandatory Hexagonal package vocabulary")
    void shouldRejectHexagonalPackageVocabularyWhenConfigurationPackagesAreInspected() {
        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.identity.configuration.application.port..",
                "io.taskmigo.identity.configuration.application.service..",
                "io.taskmigo.identity.configuration.adapter..",
                "io.taskmigo.identity.configuration.composition.."
            )
            .as(
                "Identity Configuration uses responsibility-oriented Onion packages rather than mandatory Hexagonal vocabulary"
            )
            .check(configurationProductionClasses());
    }

    @Test
    @DisplayName("keeps Configuration application code independent from outward implementations")
    void shouldKeepConfigurationApplicationIndependentWhenConfigurationPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.configuration.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.configuration.persistence..",
                "io.taskmigo.identity.configuration.configuration..",
                "org.springframework.data..",
                "jakarta.persistence.."
            )
            .check(configurationProductionClasses());
    }

    @Test
    @DisplayName("keeps published Configuration use cases independent from private implementations")
    void shouldKeepPublishedUseCasesIndependentWhenConfigurationPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.configuration.application.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.configuration.persistence..",
                "io.taskmigo.identity.configuration.configuration..",
                "io.taskmigo.identity.configuration.application.DefaultConfigurationService"
            )
            .check(configurationProductionClasses());
    }

    @Test
    @DisplayName("keeps Spring Data and JPA usage inside Configuration persistence")
    void shouldKeepPersistenceFrameworksInsideConfigurationPersistenceWhenConfigurationPackagesAreInspected() {
        noClasses()
            .that()
            .resideOutsideOfPackage("io.taskmigo.identity.configuration.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.data..", "jakarta.persistence..")
            .check(configurationProductionClasses());
    }

    private static JavaClasses configurationProductionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity.configuration");
    }
}

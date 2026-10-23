package io.taskmigo.identity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IdentityProvisioningOnionArchitectureTest {

    @Test
    @DisplayName("keeps Identity Provisioning free from mandatory Hexagonal package vocabulary")
    void shouldRejectHexagonalPackageVocabularyWhenProvisioningPackagesAreInspected() {
        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.identity.provisioning.application.port..",
                "io.taskmigo.identity.provisioning.application.service..",
                "io.taskmigo.identity.provisioning.adapter..",
                "io.taskmigo.identity.provisioning.composition.."
            )
            .as(
                "Identity Provisioning uses responsibility-oriented Onion packages rather than mandatory Hexagonal vocabulary"
            )
            .check(provisioningProductionClasses());
    }

    @Test
    @DisplayName("keeps Provisioning application code independent from configuration")
    void shouldKeepProvisioningApplicationIndependentWhenProvisioningPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.provisioning.application..")
            .and()
            .doNotHaveSimpleName("package-info")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("io.taskmigo.identity.provisioning.configuration..", "jakarta.persistence..")
            .check(provisioningProductionClasses());
    }

    @Test
    @DisplayName("keeps published Provisioning use cases independent from implementations")
    void shouldKeepPublishedUseCasesIndependentWhenProvisioningPackagesAreInspected() {
        noClasses()
            .that()
            .resideInAnyPackage("io.taskmigo.identity.provisioning.application.api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "io.taskmigo.identity.provisioning.configuration..",
                "io.taskmigo.identity.provisioning.application.DefaultGroupProvisioningService",
                "io.taskmigo.identity.provisioning.application.DefaultIdentityProvisioningService"
            )
            .check(provisioningProductionClasses());
    }

    private static JavaClasses provisioningProductionClasses() {
        return new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.identity.provisioning");
    }
}

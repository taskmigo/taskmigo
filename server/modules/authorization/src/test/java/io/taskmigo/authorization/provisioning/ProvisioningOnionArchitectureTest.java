package io.taskmigo.authorization.provisioning;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProvisioningOnionArchitectureTest {

    private static final OnionArchitectureRules.Context PROVISIONING = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization.provisioning",
        List.of("io.taskmigo.authorization.provisioning..application.."),
        List.of(),
        List.of("io.taskmigo.authorization.provisioning..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Access Control Provisioning production dependencies inward")
    void shouldKeepProvisioningProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(PROVISIONING);
    }

    @Test
    @DisplayName("keeps Access Control Provisioning tactical tests isolated from outward implementations")
    void shouldKeepProvisioningTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(PROVISIONING);
    }

    @Test
    @DisplayName("rejects retired Access Control Provisioning boundary package vocabulary")
    void shouldRejectRetiredProvisioningPackageVocabulary() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization.provisioning");

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization.provisioning..application.port.in..",
                "io.taskmigo.authorization.provisioning..application.port.out..",
                "io.taskmigo.authorization.provisioning..adapter..",
                "io.taskmigo.authorization.provisioning..composition..",
                "io.taskmigo.authorization.provisioning..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}

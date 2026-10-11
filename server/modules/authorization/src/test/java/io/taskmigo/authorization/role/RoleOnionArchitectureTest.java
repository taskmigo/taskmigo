package io.taskmigo.authorization.role;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleOnionArchitectureTest {

    private static final OnionArchitectureRules.Context ROLE = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization.role",
        List.of("io.taskmigo.authorization.role..application.."),
        List.of("io.taskmigo.authorization.role..persistence.."),
        List.of("io.taskmigo.authorization.role..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Role production dependencies inward")
    void shouldKeepRoleProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(ROLE);
    }

    @Test
    @DisplayName("keeps Role tactical tests isolated from outward implementations")
    void shouldKeepRoleTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(ROLE);
    }

    @Test
    @DisplayName("rejects retired Role Hexagonal package vocabulary")
    void shouldRejectRetiredRolePackageVocabulary() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization.role");

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization.role..application.port..",
                "io.taskmigo.authorization.role..application.service..",
                "io.taskmigo.authorization.role..adapter..",
                "io.taskmigo.authorization.role..composition..",
                "io.taskmigo.authorization.role..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}

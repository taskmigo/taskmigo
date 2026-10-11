package io.taskmigo.authorization;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccessControlOnionArchitectureTest {

    private static final OnionArchitectureRules.Context ACCESS_CONTROL = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization",
        List.of("io.taskmigo.authorization..application..", "io.taskmigo.authorization.transaction"),
        List.of("io.taskmigo.authorization..persistence..", "io.taskmigo.authorization.querybinding.jpa.."),
        List.of("io.taskmigo.authorization..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Access Control production dependencies inward")
    void shouldKeepAccessControlProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(ACCESS_CONTROL);
    }

    @Test
    @DisplayName("keeps Access Control tactical tests isolated from outward implementations")
    void shouldKeepAccessControlTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(ACCESS_CONTROL);
    }

    @Test
    @DisplayName("rejects retired shared Access Control Hexagonal package vocabulary")
    void shouldRejectRetiredSharedAccessControlPackageVocabulary() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization");

        noClasses()
            .should()
            .resideInAnyPackage("io.taskmigo.authorization.application.port..", "io.taskmigo.authorization.adapter..")
            .allowEmptyShould(true)
            .check(classes);
    }
}

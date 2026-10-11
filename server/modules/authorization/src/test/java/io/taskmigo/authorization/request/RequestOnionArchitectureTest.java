package io.taskmigo.authorization.request;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestOnionArchitectureTest {

    private static final OnionArchitectureRules.Context REQUEST = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization.request",
        List.of("io.taskmigo.authorization.request..application.."),
        List.of("io.taskmigo.authorization.request..persistence.."),
        List.of("io.taskmigo.authorization.request..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Request Authorization production dependencies inward")
    void shouldKeepRequestAuthorizationProductionDependenciesInwardWhenRequestPackagesAreInspected() {
        OnionArchitectureRules.checkProduction(REQUEST);
    }

    @Test
    @DisplayName("keeps Request Authorization tactical tests isolated from outward implementations")
    void shouldKeepRequestAuthorizationTacticalTestsIsolatedWhenRequestTestsAreInspected() {
        OnionArchitectureRules.checkTacticalTests(REQUEST);
    }

    @Test
    @DisplayName("rejects retired Request Authorization boundary package vocabulary")
    void shouldRejectRetiredRequestAuthorizationPackageVocabularyWhenRequestPackagesAreInspected() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization.request");

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization.request..application.port.in..",
                "io.taskmigo.authorization.request..adapter..",
                "io.taskmigo.authorization.request..composition..",
                "io.taskmigo.authorization.request..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}

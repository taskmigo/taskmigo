package io.taskmigo.authorization.object;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectOnionArchitectureTest {

    private static final OnionArchitectureRules.Context OBJECT = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization.object",
        List.of("io.taskmigo.authorization.object..application.."),
        List.of(),
        List.of("io.taskmigo.authorization.object..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Object Authorization production dependencies inward")
    void shouldKeepObjectAuthorizationProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(OBJECT);
    }

    @Test
    @DisplayName("keeps Object Authorization tactical tests isolated from outward implementations")
    void shouldKeepObjectAuthorizationTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(OBJECT);
    }

    @Test
    @DisplayName("rejects retired Object Authorization boundary package vocabulary")
    void shouldRejectRetiredObjectAuthorizationPackageVocabulary() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization.object");

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization.object..application.port.in..",
                "io.taskmigo.authorization.object..application.port.out..",
                "io.taskmigo.authorization.object..adapter..",
                "io.taskmigo.authorization.object..composition..",
                "io.taskmigo.authorization.object..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}

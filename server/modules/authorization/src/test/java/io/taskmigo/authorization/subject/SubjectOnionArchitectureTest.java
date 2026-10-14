package io.taskmigo.authorization.subject;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubjectOnionArchitectureTest {

    private static final OnionArchitectureRules.Context SUBJECT = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization.subject",
        List.of("io.taskmigo.authorization.subject..application.."),
        List.of("io.taskmigo.authorization.subject..persistence.."),
        List.of("io.taskmigo.authorization.subject..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Subject production dependencies inward")
    void shouldKeepSubjectProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(SUBJECT);
    }

    @Test
    @DisplayName("keeps Subject tactical tests isolated from outward implementations")
    void shouldKeepSubjectTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(SUBJECT);
    }

    @Test
    @DisplayName("rejects retired Subject Hexagonal package vocabulary")
    void shouldRejectRetiredSubjectPackageVocabulary() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization.subject");

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization.subject..application.port..",
                "io.taskmigo.authorization.subject..application.service..",
                "io.taskmigo.authorization.subject..adapter..",
                "io.taskmigo.authorization.subject..composition..",
                "io.taskmigo.authorization.subject..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}

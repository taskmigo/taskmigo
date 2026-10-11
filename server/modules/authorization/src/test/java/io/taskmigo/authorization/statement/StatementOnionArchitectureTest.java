package io.taskmigo.authorization.statement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.taskmigo.architecture.OnionArchitectureRules;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementOnionArchitectureTest {

    private static final OnionArchitectureRules.Context STATEMENT = new OnionArchitectureRules.Context(
        "io.taskmigo.authorization.statement",
        List.of("io.taskmigo.authorization.statement..application.."),
        List.of("io.taskmigo.authorization.statement..persistence.."),
        List.of("io.taskmigo.authorization.statement..configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("keeps Statement production dependencies inward")
    void shouldKeepStatementProductionDependenciesInward() {
        OnionArchitectureRules.checkProduction(STATEMENT);
    }

    @Test
    @DisplayName("keeps Statement tactical tests isolated from outward implementations")
    void shouldKeepStatementTacticalTestsIsolated() {
        OnionArchitectureRules.checkTacticalTests(STATEMENT);
    }

    @Test
    @DisplayName("rejects retired Statement Hexagonal package vocabulary")
    void shouldRejectRetiredStatementPackageVocabulary() {
        JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.taskmigo.authorization.statement");

        noClasses()
            .should()
            .resideInAnyPackage(
                "io.taskmigo.authorization.statement..application.port..",
                "io.taskmigo.authorization.statement..application.service..",
                "io.taskmigo.authorization.statement..adapter..",
                "io.taskmigo.authorization.statement..composition..",
                "io.taskmigo.authorization.statement..infrastructure.."
            )
            .allowEmptyShould(true)
            .check(classes);
    }
}

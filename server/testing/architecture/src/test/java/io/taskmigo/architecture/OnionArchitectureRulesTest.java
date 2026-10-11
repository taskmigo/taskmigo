package io.taskmigo.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.taskmigo.architecture.OnionArchitectureRules.Context;
import io.taskmigo.architecture.onionfixtures.application.ApplicationFrameworkViolation;
import io.taskmigo.architecture.onionfixtures.application.ApplicationPersistenceViolation;
import io.taskmigo.architecture.onionfixtures.module.ExecutableApplicationLeak;
import io.taskmigo.architecture.onionfixtures.persistence.PersistenceComponent;
import io.taskmigo.architecture.onionfixtures.support.FrameworkLeak;
import io.taskmigo.web.ArchitectureFixture;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OnionArchitectureRulesTest {

    private static final String ROOT = "io.taskmigo.architecture.onionfixtures";
    private static final Context CONTEXT = new Context(
        ROOT,
        List.of(ROOT + ".application.."),
        List.of(ROOT + ".persistence.."),
        List.of(ROOT + ".configuration.."),
        List.of(),
        List.of()
    );

    @Test
    @DisplayName("rejects domain dependencies on application code")
    void shouldRejectApplicationDependencyWhenDomainRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importPackages(ROOT + ".domain", ROOT + ".application");

        assertThatThrownBy(() -> OnionArchitectureRules.domainRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }

    @Test
    @DisplayName("rejects application dependencies on persistence implementations")
    void shouldRejectPersistenceDependencyWhenApplicationRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(
            ApplicationPersistenceViolation.class,
            PersistenceComponent.class
        );

        assertThatThrownBy(() -> OnionArchitectureRules.applicationRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }

    @Test
    @DisplayName("rejects framework dependencies in ordinary application code")
    void shouldRejectFrameworkDependencyWhenApplicationRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(ApplicationFrameworkViolation.class);

        assertThatThrownBy(() -> OnionArchitectureRules.applicationRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }

    @Test
    @DisplayName("rejects persistence frameworks outside persistence responsibilities")
    void shouldRejectFrameworkLeakWhenPersistenceContainmentRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(FrameworkLeak.class);

        assertThatThrownBy(() ->
            OnionArchitectureRules.persistenceFrameworkContainmentRule(CONTEXT).check(classes)
        ).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("allows persistence frameworks inside explicit persistence responsibilities")
    void shouldAllowFrameworkDependencyWhenPersistenceResponsibilityIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(PersistenceComponent.class);

        assertThatCode(() ->
            OnionArchitectureRules.persistenceFrameworkContainmentRule(CONTEXT).check(classes)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects reusable modules that depend on executable applications")
    void shouldRejectExecutableApplicationDependencyWhenModuleRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(
            ExecutableApplicationLeak.class,
            ArchitectureFixture.class
        );

        assertThatThrownBy(() -> OnionArchitectureRules.moduleIsolationRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }
}

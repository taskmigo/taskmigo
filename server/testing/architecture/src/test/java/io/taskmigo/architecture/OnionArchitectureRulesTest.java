package io.taskmigo.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.taskmigo.architecture.OnionArchitectureRules.Context;
import io.taskmigo.architecture.onionfixtures.application.ApplicationFrameworkViolation;
import io.taskmigo.architecture.onionfixtures.application.ApplicationPersistenceViolation;
import io.taskmigo.architecture.onionfixtures.application.ApplicationTransactionManagementConfigurationViolation;
import io.taskmigo.architecture.onionfixtures.application.ApplicationTransactionTemplateViolation;
import io.taskmigo.architecture.onionfixtures.application.ApplicationTransactionalContract;
import io.taskmigo.architecture.onionfixtures.application.ApplicationTransactionalService;
import io.taskmigo.architecture.onionfixtures.domain.DomainTransactionalViolation;
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

    /**
     * Verifies that application implementations may declare Spring transaction metadata without opening the
     * application layer to unrelated framework dependencies.
     *
     * Given: an application fixture whose only Spring dependency is {@code @Transactional}.
     * Expect: the shared application rule accepts the fixture.
     */
    @Test
    @DisplayName("allows transaction annotations in application code")
    void shouldAllowTransactionalAnnotationWhenApplicationRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(ApplicationTransactionalService.class);

        assertThatCode(() -> OnionArchitectureRules.applicationRule(CONTEXT).check(classes)).doesNotThrowAnyException();
    }

    /**
     * Verifies that the application exception is an exact type allowlist rather than the whole annotation package.
     *
     * Given: an application fixture using Spring transaction-management configuration metadata.
     * Expect: the shared application rule rejects it even though the type lives in the transaction annotation package.
     */
    @Test
    @DisplayName("rejects non-approved transaction annotation types in application code")
    void shouldRejectNonApprovedTransactionAnnotationTypeWhenApplicationRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(
            ApplicationTransactionManagementConfigurationViolation.class
        );

        assertThatThrownBy(() -> OnionArchitectureRules.applicationRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }

    /**
     * Verifies that published/application contracts remain framework-neutral while implementations may use
     * declarative transaction metadata.
     *
     * Given: an application interface annotated with Spring {@code @Transactional}.
     * Expect: the application-contract rule rejects the interface.
     */
    @Test
    @DisplayName("rejects transaction annotations on application interfaces")
    void shouldRejectTransactionalAnnotationWhenApplicationContractRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(ApplicationTransactionalContract.class);

        assertThatThrownBy(() -> OnionArchitectureRules.applicationContractRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }

    /**
     * Verifies that allowing declarative transaction metadata does not permit programmatic transaction mechanics.
     *
     * Given: an application fixture that depends on {@code TransactionTemplate}.
     * Expect: the shared application rule rejects the fixture.
     */
    @Test
    @DisplayName("rejects TransactionTemplate in application code")
    void shouldRejectTransactionTemplateWhenApplicationRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(ApplicationTransactionTemplateViolation.class);

        assertThatThrownBy(() -> OnionArchitectureRules.applicationRule(CONTEXT).check(classes)).isInstanceOf(
            AssertionError.class
        );
    }

    /**
     * Verifies that the domain layer remains completely Spring-neutral after the application exception is introduced.
     *
     * Given: a domain fixture annotated with Spring {@code @Transactional}.
     * Expect: the shared domain rule rejects the fixture.
     */
    @Test
    @DisplayName("rejects transaction annotations in domain code")
    void shouldRejectTransactionalAnnotationWhenDomainRuleIsChecked() {
        JavaClasses classes = new ClassFileImporter().importClasses(DomainTransactionalViolation.class);

        assertThatThrownBy(() -> OnionArchitectureRules.domainRule(CONTEXT).check(classes)).isInstanceOf(
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

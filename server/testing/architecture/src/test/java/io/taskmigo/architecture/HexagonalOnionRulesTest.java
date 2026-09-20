package io.taskmigo.architecture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.taskmigo.architecture.HexagonalOnionRules.Context;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HexagonalOnionRulesTest {

    private static final String ROOT = "io.taskmigo.architecture.fixtures";
    private static final Context CONTEXT = new Context(
        ROOT,
        List.of(ROOT + "..application.."),
        List.of(ROOT + "..legacy.."),
        List.of(),
        List.of(ROOT + "..legacy.."),
        List.of()
    );
    private static final JavaClasses CLASSES = new ClassFileImporter().importPackages(ROOT);

    @Test
    @DisplayName("rejects domain dependencies on application code")
    void shouldRejectApplicationDependencyWhenDomainRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.domainRule(CONTEXT).check(CLASSES)).isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects application dependencies on legacy adapters")
    void shouldRejectAdapterDependencyWhenApplicationRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.applicationRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects framework dependencies in target application services")
    void shouldRejectFrameworkDependencyWhenPlainApplicationServiceRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.plainApplicationServiceRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects inbound ports that depend on application implementations")
    void shouldRejectImplementationDependencyWhenInboundPortRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.inboundPortRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects outbound ports that depend on driven adapters")
    void shouldRejectDrivenAdapterDependencyWhenOutboundPortRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.outboundPortRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects driving adapters that depend on application implementations")
    void shouldRejectImplementationDependencyWhenDrivingAdapterRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.drivingAdapterRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects driven adapters that depend on application implementations")
    void shouldRejectImplementationDependencyWhenDrivenAdapterRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.drivenAdapterRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects persistence frameworks outside persistence adapters")
    void shouldRejectPersistenceFrameworkDependencyWhenContainmentRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.persistenceFrameworkContainmentRule(CONTEXT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }

    @Test
    @DisplayName("rejects executable driving adapters that bypass inbound ports")
    void shouldRejectImplementationDependencyWhenExecutableDrivingAdapterRuleIsChecked() {
        assertThatThrownBy(() -> HexagonalOnionRules.drivingApplicationRule(ROOT).check(CLASSES))
            .isInstanceOf(AssertionError.class);
    }
}

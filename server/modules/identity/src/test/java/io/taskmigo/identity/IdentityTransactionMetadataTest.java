package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.identity.group.application.DefaultGroupService;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

class IdentityTransactionMetadataTest {

    private final AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

    /**
     * Verifies that Group list remains an ordinary read-only REQUIRED use case after the runner is removed.
     *
     * Given: the production DefaultGroupService list method.
     * Expect: Spring resolves readOnly=true and PROPAGATION_REQUIRED for that method.
     */
    @Test
    @DisplayName("keeps Group list read-only and REQUIRED")
    void shouldExposeReadOnlyRequiredMetadataWhenGroupListIsInspected() {
        // Arrange
        Method list = method("list", 4);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(list, DefaultGroupService.class);

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isTrue();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Verifies that both public Group create overloads own an effective transaction boundary.
     *
     * Given: both production create overloads, including the short overload that delegates by self-invocation.
     * Expect: Spring resolves non-read-only PROPAGATION_REQUIRED metadata for both overloads.
     */
    @Test
    @DisplayName("keeps both Group create overloads transactional")
    void shouldExposeRequiredWriteMetadataWhenGroupCreateOverloadsAreInspected() {
        // Arrange
        List<Method> creates = Arrays.stream(DefaultGroupService.class.getMethods())
            .filter(method -> method.getName().equals("create"))
            .toList();

        // Act
        List<TransactionAttribute> attributes = creates
            .stream()
            .map(method ->
                Objects.requireNonNull(this.attributes.getTransactionAttribute(method, DefaultGroupService.class))
            )
            .toList();

        // Assert
        assertThat(creates).hasSize(2);
        assertThat(attributes).allSatisfy(attribute -> {
            assertThat(attribute).isNotNull();
            assertThat(attribute.isReadOnly()).isFalse();
            assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        });
    }

    private static Method method(String name, int parameterCount) {
        return Arrays.stream(DefaultGroupService.class.getMethods())
            .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
            .findFirst()
            .orElseThrow();
    }
}

package io.taskmigo.authorization.request.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

class EffectiveStatementTransactionMetadataTest {

    private final AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

    /**
     * Verifies that effective Statement resolution keeps its specialized consistency boundary.
     *
     * Given: the production JpaEffectiveStatementResolver resolve method.
     * Expect: Spring resolves read-only, REQUIRES_NEW, REPEATABLE_READ transaction metadata.
     */
    @Test
    @DisplayName("keeps effective Statement resolution REQUIRES_NEW and REPEATABLE_READ")
    void shouldPreserveSpecializedMetadataWhenEffectiveStatementResolverIsInspected() throws Exception {
        // Arrange
        Method method = JpaEffectiveStatementResolver.class.getMethod("resolve", UUID.class);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(
            method,
            JpaEffectiveStatementResolver.class
        );

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isTrue();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(attribute.getIsolationLevel()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
}

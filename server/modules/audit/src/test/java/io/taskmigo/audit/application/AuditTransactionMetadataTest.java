package io.taskmigo.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.audit.event.AuditEvent;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

class AuditTransactionMetadataTest {

    private final AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

    /**
     * Verifies that synchronous audit append still requires a caller-owned transaction.
     *
     * Given: the production DefaultAuditAppendService append method.
     * Expect: Spring resolves PROPAGATION_MANDATORY and a non-read-only transaction attribute.
     */
    @Test
    @DisplayName("keeps audit append MANDATORY")
    void shouldExposeMandatoryWriteMetadataWhenAuditAppendIsInspected() throws Exception {
        // Arrange
        Method method = DefaultAuditAppendService.class.getMethod("append", AuditEvent.class);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(
            method,
            DefaultAuditAppendService.class
        );

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_MANDATORY);
        assertThat(attribute.isReadOnly()).isFalse();
    }

    /**
     * Verifies that privacy scrubbing still participates only in an existing business transaction.
     *
     * Given: the production DefaultAuditPrivacyService scrubUser method.
     * Expect: Spring resolves PROPAGATION_MANDATORY and a non-read-only transaction attribute.
     */
    @Test
    @DisplayName("keeps audit privacy scrubbing MANDATORY")
    void shouldExposeMandatoryWriteMetadataWhenAuditPrivacyIsInspected() throws Exception {
        // Arrange
        Method method = DefaultAuditPrivacyService.class.getMethod("scrubUser", UUID.class);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(
            method,
            DefaultAuditPrivacyService.class
        );

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_MANDATORY);
        assertThat(attribute.isReadOnly()).isFalse();
    }

    /**
     * Verifies that audit queries remain ordinary read-only REQUIRED transactions.
     *
     * Given: the production DefaultAuditQueryService list method.
     * Expect: Spring resolves PROPAGATION_REQUIRED with readOnly=true.
     */
    @Test
    @DisplayName("keeps audit queries read-only and REQUIRED")
    void shouldExposeReadOnlyRequiredMetadataWhenAuditQueryIsInspected() throws Exception {
        // Arrange
        Method method = DefaultAuditQueryService.class.getMethod("list", String.class, int.class, int.class);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(
            method,
            DefaultAuditQueryService.class
        );

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
        assertThat(attribute.isReadOnly()).isTrue();
    }
}

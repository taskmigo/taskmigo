package io.taskmigo.identity.integration.accesscontrol;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.subject.SubjectRef;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

class IdentityEffectiveSubjectTransactionMetadataTest {

    private final AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

    /**
     * Verifies that principal subject resolution keeps its integration-level read-only transaction boundary.
     *
     * Given: the production IdentityEffectiveSubjectResolver resolve method.
     * Expect: Spring resolves REQUIRED propagation with readOnly=true.
     */
    @Test
    @DisplayName("keeps principal subject resolution read-only and REQUIRED")
    void shouldPreserveReadOnlyMetadataWhenResolveIsInspected() throws Exception {
        // Arrange
        Method method = IdentityEffectiveSubjectResolver.class.getMethod("resolve", UUID.class);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(
            method,
            IdentityEffectiveSubjectResolver.class
        );

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isTrue();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }

    /**
     * Verifies that subject expansion keeps its integration-level read-only transaction boundary.
     *
     * Given: the production IdentityEffectiveSubjectResolver expand method.
     * Expect: Spring resolves REQUIRED propagation with readOnly=true.
     */
    @Test
    @DisplayName("keeps subject expansion read-only and REQUIRED")
    void shouldPreserveReadOnlyMetadataWhenExpandIsInspected() throws Exception {
        // Arrange
        Method method = IdentityEffectiveSubjectResolver.class.getMethod("expand", SubjectRef.class);

        // Act
        TransactionAttribute attribute = this.attributes.getTransactionAttribute(
            method,
            IdentityEffectiveSubjectResolver.class
        );

        // Assert
        assertThat(attribute).isNotNull();
        assertThat(attribute.isReadOnly()).isTrue();
        assertThat(attribute.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRED);
    }
}

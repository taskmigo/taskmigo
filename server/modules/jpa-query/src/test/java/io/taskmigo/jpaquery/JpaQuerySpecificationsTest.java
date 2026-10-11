package io.taskmigo.jpaquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class JpaQuerySpecificationsTest {

    @Mock
    private Root<TestEntity> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Predicate authorizationPredicate;

    @Mock
    private Predicate clientCondition;

    @Mock
    private Predicate tautology;

    @Mock
    private Predicate clientOr;

    @Mock
    private Predicate combined;

    /**
     * Given: mandatory Object Authorization and an untrusted client filter equivalent to `condition || true`.
     * Expect: the client OR remains nested below a system-owned top-level authorization AND.
     */
    @Test
    @DisplayName("keeps mandatory authorization outside a client OR tautology")
    void shouldKeepAuthorizationOutsideClientOrWhenComposing() {
        // Arrange
        Specification<TestEntity> authorization = (root, query, builder) -> this.authorizationPredicate;
        Specification<TestEntity> clientFilter = (root, query, builder) ->
            builder.or(this.clientCondition, builder.conjunction());
        when(this.builder.conjunction()).thenReturn(this.tautology);
        when(this.builder.or(this.clientCondition, this.tautology)).thenReturn(this.clientOr);
        when(this.builder.and(this.authorizationPredicate, this.clientOr)).thenReturn(this.combined);

        // Act
        Predicate result = JpaQuerySpecifications.authorized(authorization, clientFilter).toPredicate(
            this.root,
            this.query,
            this.builder
        );

        // Assert
        assertThat(result).isSameAs(this.combined);
        verify(this.builder).or(this.clientCondition, this.tautology);
        verify(this.builder).and(this.authorizationPredicate, this.clientOr);
    }

    private static final class TestEntity {}
}

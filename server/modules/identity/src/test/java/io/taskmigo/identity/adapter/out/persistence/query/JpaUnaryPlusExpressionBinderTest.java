package io.taskmigo.identity.adapter.out.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.query.model.QueryExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

class JpaUnaryPlusExpressionBinderTest {

    /**
     * Verifies that Query unary PLUS is bound as the numeric identity operation.
     *
     * Given: a comparison whose operands are unary PLUS over a bound numeric object field.
     * Expect: the JPA predicate compares the original field expression directly without adding a cast or negation.
     */
    @Test
    @DisplayName("should bind unary plus when query value is numeric")
    void shouldBindUnaryPlusWhenQueryValueIsNumeric() {
        // Arrange
        QueryExpression.Reference amount = new QueryExpression.Reference("object", List.of("amount"));
        QueryExpression plus = new QueryExpression.Unary(QueryExpression.UnaryOperator.PLUS, amount);
        QueryExpression expression = new QueryExpression.Binary(QueryExpression.BinaryOperator.EQUAL, plus, plus);
        Specification<TestEntity> specification = JpaQueryExpressionBinder.bind(
            expression,
            Map.of("amount", "amount"),
            Map.of("amount", Integer.class)
        );

        // Act + Assert
        assertBindsAsIdentity(specification);
    }

    /**
     * Verifies that Object Authorization unary PLUS is bound as the numeric identity operation.
     *
     * Given: a comparison whose operands are unary PLUS over a bound numeric object field.
     * Expect: the JPA predicate compares the original field expression directly without adding a cast or negation.
     */
    @Test
    @DisplayName("should bind unary plus when object authorization value is numeric")
    void shouldBindUnaryPlusWhenObjectAuthorizationValueIsNumeric() {
        // Arrange
        ObjectAuthorizationExpression.Reference amount = new ObjectAuthorizationExpression.Reference(
            "object",
            List.of("amount")
        );
        ObjectAuthorizationExpression plus = new ObjectAuthorizationExpression.Unary(
            ObjectAuthorizationExpression.UnaryOperator.PLUS,
            amount
        );
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.EQUAL,
            plus,
            plus
        );
        Specification<TestEntity> specification = JpaObjectAuthorizationExpressionBinder.bind(
            expression,
            Map.of("amount", "amount"),
            Map.of("amount", Integer.class)
        );

        // Act + Assert
        assertBindsAsIdentity(specification);
    }

    private static void assertBindsAsIdentity(Specification<TestEntity> specification) {
        Root<TestEntity> root = root();
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Expression<?> amount = root.get("amount");
        Predicate expected = mock(Predicate.class);
        when(builder.equal(amount, amount)).thenReturn(expected);

        assertThat(specification.toPredicate(root, query, builder)).isSameAs(expected);
    }

    @SuppressWarnings("unchecked")
    private static Root<TestEntity> root() {
        return (Root<TestEntity>) mock(Root.class, RETURNS_DEEP_STUBS);
    }

    private static final class TestEntity {}
}

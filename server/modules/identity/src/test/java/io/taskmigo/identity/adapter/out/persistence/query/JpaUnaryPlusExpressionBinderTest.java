package io.taskmigo.identity.adapter.out.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationFieldBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.StaticObjectAuthorizationBinding;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.QueryBinding;
import io.taskmigo.query.QueryFieldBinding;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.StaticQueryBinding;
import io.taskmigo.query.model.QueryExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class JpaUnaryPlusExpressionBinderTest {

    private static final ResourceType RESOURCE = ResourceType.of("resource:test");
    private static final FieldId AMOUNT = FieldId.of("field:test:amount");

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Root<TestEntity> root;

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
        QueryExpression.Reference amount = new QueryExpression.Reference("object", List.of("amount"), AMOUNT);
        QueryExpression plus = new QueryExpression.Unary(QueryExpression.UnaryOperator.PLUS, amount);
        QueryExpression expression = new QueryExpression.Binary(QueryExpression.BinaryOperator.EQUAL, plus, plus);
        Specification<TestEntity> specification = JpaQueryExpressionBinder.bind(
            expression,
            Map.of(AMOUNT, "amount"),
            Map.of(AMOUNT, Integer.class)
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
            List.of("amount"),
            AMOUNT
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
            Map.of(AMOUNT, "amount"),
            Map.of(AMOUNT, Integer.class)
        );

        // Act + Assert
        assertBindsAsIdentity(specification);
    }

    /**
     * Verifies the JPA query binder consumes the execution path from the selected QueryBinding.
     */
    @Test
    @DisplayName("should translate query fields through the selected execution binding")
    void shouldTranslateQueryFieldThroughSelectedExecutionBinding() {
        ResourceSchema schema = ResourceSchema.of(
            RESOURCE,
            List.of(new Field(AMOUNT, FieldPath.parse("amount"), LanguageType.Scalar.NUMBER, false))
        );
        QueryBinding<TestQuery> binding = new StaticQueryBinding<>(
            TestQuery.class,
            schema,
            List.of(new QueryFieldBinding(AMOUNT, QueryPath.parse("storedAmount"), Set.of(QueryOperator.EQ)))
        );
        var predicate = new FilterByCompiler().compile(schema, binding, "object.amount == 1");
        JpaQueryPredicateBinder<TestQuery, TestEntity> binder = new JpaQueryPredicateBinder<>(
            TestEntity.class,
            binding,
            Map.of(AMOUNT, Integer.class)
        );

        Specification<TestEntity> specification = binder.bind(predicate);

        assertBindsStoredAmount(specification);
    }

    /**
     * Verifies the JPA Object Authorization binder consumes the execution path from the selected object binding.
     */
    @Test
    @DisplayName("should translate object authorization fields through the selected execution binding")
    void shouldTranslateObjectAuthorizationFieldThroughSelectedExecutionBinding() {
        ResourceSchema schema = ResourceSchema.of(
            RESOURCE,
            List.of(new Field(AMOUNT, FieldPath.parse("amount"), LanguageType.Scalar.NUMBER, false))
        );
        ObjectAuthorizationBinding<TestQuery> binding = new StaticObjectAuthorizationBinding<>(
            TestQuery.class,
            schema,
            List.of(
                new ObjectAuthorizationFieldBinding(
                    AMOUNT,
                    "storedAmount",
                    Set.of(ObjectAuthorizationOperator.EQ)
                )
            )
        );
        var predicate = ObjectAuthorizationPredicateModels.from(
            binding,
            new ObjectAuthorizationExpression.Binary(
                ObjectAuthorizationExpression.BinaryOperator.EQUAL,
                new ObjectAuthorizationExpression.Reference("object", List.of("amount"), AMOUNT),
                new ObjectAuthorizationExpression.Literal(1)
            )
        );
        JpaObjectAuthorizationPredicateBinder<TestQuery, TestEntity> binder =
            new JpaObjectAuthorizationPredicateBinder<>(
                TestEntity.class,
                binding,
                Map.of(AMOUNT, Integer.class)
            );

        Specification<TestEntity> specification = binder.bind(predicate);

        assertBindsStoredAmount(specification);
    }

    private void assertBindsStoredAmount(Specification<TestEntity> specification) {
        Root<TestEntity> root = this.root;
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Expression<?> amount = root.get("storedAmount");
        Predicate expected = mock(Predicate.class);
        when(builder.equal(amount, builder.literal(1))).thenReturn(expected);

        assertThat(specification.toPredicate(root, query, builder)).isSameAs(expected);
    }

    private void assertBindsAsIdentity(Specification<TestEntity> specification) {
        Root<TestEntity> root = this.root;
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder builder = mock(CriteriaBuilder.class);
        Expression<?> amount = root.get("amount");
        Predicate expected = mock(Predicate.class);
        when(builder.equal(amount, amount)).thenReturn(expected);

        assertThat(specification.toPredicate(root, query, builder)).isSameAs(expected);
    }

    private static final class TestQuery {}

    private static final class TestEntity {}
}

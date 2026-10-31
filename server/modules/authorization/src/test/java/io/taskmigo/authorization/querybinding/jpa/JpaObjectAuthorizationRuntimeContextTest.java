package io.taskmigo.authorization.querybinding.jpa;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.jpaquery.JpaQueryValueExpression;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaObjectAuthorizationRuntimeContextTest {

    @Test
    @DisplayName("JPA Object Authorization binder requires the exact runtime Authorization schema identity")
    void shouldRequireMatchingRuntimeSchemaIdentity() {
        TestSchema schema = schema();
        QueryFieldContext scoped = new QueryFieldContext(Map.of("scope", "incident"));
        ObjectAuthorizationPredicate<TestObject> predicate = ObjectAuthorizationPredicateModels.from(
            schema,
            scoped,
            new ObjectAuthorizationExpression.Literal(true)
        );
        JpaObjectAuthorizationPredicateBinder<TestObject, TestEntity> binder =
            new JpaObjectAuthorizationPredicateBinder<>(TestObject.class, schema);

        assertThatCode(() -> binder.bind(predicate, scoped)).doesNotThrowAnyException();
        assertThatThrownBy(() -> binder.bind(predicate, QueryFieldContext.empty()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("schema identity");
    }

    private static TestSchema schema() {
        JpaQueryValueExpression<TestEntity, String> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<String> resolve(Root<TestEntity> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                return mock(Expression.class);
            }

            @Override
            public Class<String> javaType() {
                return String.class;
            }
        };
        QueryField<TestEntity, String> severity = QueryField.dynamic(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            String.class,
            expression
        ).operators(QueryOperator.EQ);
        return new TestSchema(severity);
    }

    private static final class TestSchema
        extends QuerySchema<TestEntity>
        implements ObjectAuthorizationSchema<TestObject>
    {

        private final QueryField<TestEntity, String> severity;

        private TestSchema(QueryField<TestEntity, String> severity) {
            super(TestEntity.class);
            this.severity = severity;
        }

        @Override
        protected String operationId() {
            return "test.authorization";
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> staticFields() {
            return List.of();
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
            return "incident".equals(context.values().get("scope")) ? List.of(this.severity) : List.of();
        }
    }

    private static final class TestEntity {}

    private static final class TestObject {}
}

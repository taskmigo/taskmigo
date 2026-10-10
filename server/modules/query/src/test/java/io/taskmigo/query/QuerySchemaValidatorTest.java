package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.model.QueryExpression;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuerySchemaValidatorTest {

    @Test
    @DisplayName("should map greater-or-equal to GTE capability")
    void shouldMapGreaterOrEqualToGteCapability() {
        QuerySchema<TestQuery> schema = schema("age", Integer.class, Set.of(QueryOperator.GTE));
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.GREATER_OR_EQUAL,
            reference("age"),
            new QueryExpression.Literal(18)
        );

        assertThatCode(() -> QuerySchemaValidator.validate(expression, schema)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should require CONTAINS capability for contains expression")
    void shouldRequireContainsCapabilityForContainsExpression() {
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.CONTAINS,
            reference("name"),
            new QueryExpression.Literal("ali")
        );

        assertThatCode(() ->
            QuerySchemaValidator.validate(expression, schema("name", String.class, Set.of(QueryOperator.CONTAINS)))
        ).doesNotThrowAnyException();
        assertThatThrownBy(() ->
            QuerySchemaValidator.validate(expression, schema("name", String.class, Set.of(QueryOperator.EQ)))
        )
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("operator is not supported");
    }

    @Test
    @DisplayName("should reject contains when the substring is not a literal")
    void shouldRejectContainsWhenSubstringIsNotLiteral() {
        QuerySchema<TestQuery> schema = schema("name", String.class, Set.of(QueryOperator.CONTAINS));
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.CONTAINS,
            reference("name"),
            reference("name")
        );

        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("string literal");
    }

    @Test
    @DisplayName("should reject legacy membership expressions")
    void shouldRejectLegacyMembershipExpression() {
        QuerySchema<TestQuery> schema = schema("name", String.class, Set.of(QueryOperator.EQ));
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.IN,
            reference("name"),
            new QueryExpression.ListValue(List.of(new QueryExpression.Literal("alice")))
        );

        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("unsupported filterBy operator");
    }

    @Test
    @DisplayName("should reject unary expressions from the query surface")
    void shouldRejectUnaryExpression() {
        QuerySchema<TestQuery> schema = schema("active", Boolean.class, Set.of(QueryOperator.EQ));
        QueryExpression expression = new QueryExpression.Unary(QueryExpression.UnaryOperator.NOT, reference("active"));

        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("unsupported filterBy expression");
    }

    private static QueryExpression.Reference reference(String path) {
        return new QueryExpression.Reference("object", List.of(path));
    }

    private static QuerySchema<TestQuery> schema(String path, Class<?> type, Set<QueryOperator> operators) {
        QueryField field = new QueryField(QueryPath.of(path), TypeDescriptor.of(type), false, operators);
        return new QuerySchema<>() {
            @Override
            public Class<TestQuery> queryType() {
                return TestQuery.class;
            }

            @Override
            public Optional<QueryField> field(QueryPath queryPath) {
                return field.path().equals(queryPath) ? Optional.of(field) : Optional.empty();
            }

            @Override
            public Collection<QueryField> fields() {
                return List.of(field);
            }
        };
    }

    private static final class TestQuery {}
}

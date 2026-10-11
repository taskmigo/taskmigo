package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.model.QueryExpression;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuerySchemaValidatorTest {

    @Test
    @DisplayName("should map greater-or-equal to GTE capability")
    void shouldMapGreaterOrEqualToGteCapability() {
        QuerySchemaView schema = schema("age", Integer.class, Set.of(QueryOperator.GTE));
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.GREATER_OR_EQUAL,
            reference("age"),
            new QueryExpression.Literal(18)
        );

        assertThatCode(() ->
            QuerySchemaValidator.validate(expression, schema, QueryFieldContext.empty())
        ).doesNotThrowAnyException();
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
            QuerySchemaValidator.validate(
                expression,
                schema("name", String.class, Set.of(QueryOperator.CONTAINS)),
                QueryFieldContext.empty()
            )
        ).doesNotThrowAnyException();
        assertThatThrownBy(() ->
            QuerySchemaValidator.validate(
                expression,
                schema("name", String.class, Set.of(QueryOperator.EQ)),
                QueryFieldContext.empty()
            )
        )
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("operator is not supported");
    }

    @Test
    @DisplayName("should reject contains when the substring is not a literal")
    void shouldRejectContainsWhenSubstringIsNotLiteral() {
        QuerySchemaView schema = schema("name", String.class, Set.of(QueryOperator.CONTAINS));
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.CONTAINS,
            reference("name"),
            reference("name")
        );

        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema, QueryFieldContext.empty()))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("string literal");
    }

    @Test
    @DisplayName("should reject legacy membership expressions")
    void shouldRejectLegacyMembershipExpression() {
        QuerySchemaView schema = schema("name", String.class, Set.of(QueryOperator.EQ));
        QueryExpression expression = new QueryExpression.Binary(
            QueryExpression.BinaryOperator.IN,
            reference("name"),
            new QueryExpression.ListValue(List.of(new QueryExpression.Literal("alice")))
        );

        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema, QueryFieldContext.empty()))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("unsupported filterBy operator");
    }

    @Test
    @DisplayName("should reject unary expressions from the query surface")
    void shouldRejectUnaryExpression() {
        QuerySchemaView schema = schema("active", Boolean.class, Set.of(QueryOperator.EQ));
        QueryExpression expression = new QueryExpression.Unary(QueryExpression.UnaryOperator.NOT, reference("active"));

        assertThatThrownBy(() -> QuerySchemaValidator.validate(expression, schema, QueryFieldContext.empty()))
            .isInstanceOf(FilterByException.class)
            .hasMessageContaining("unsupported filterBy expression");
    }

    private static QueryExpression.Reference reference(String path) {
        return new QueryExpression.Reference("object", List.of(path));
    }

    private static QuerySchemaView schema(String path, Class<?> type, Set<QueryOperator> operators) {
        QueryFieldDescriptor field = new QueryFieldDescriptor(
            QueryPath.of(path),
            TypeDescriptor.of(type),
            false,
            operators
        );
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return "test.query";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of(field);
            }
        };
    }
}

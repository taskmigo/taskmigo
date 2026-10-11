package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Enhancement237ObjectAuthorizationContractTest {

    @Test
    @DisplayName("validates object authorization against QuerySchemaView including contains")
    void shouldValidateAgainstQuerySchemaViewIncludingContains() {
        QuerySchemaView schema = schema(
            new QueryFieldDescriptor(
                QueryPath.parse("name"),
                TypeDescriptor.of(String.class),
                false,
                Set.of(QueryOperator.EQ, QueryOperator.CONTAINS)
            )
        );
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.CONTAINS,
            new ObjectAuthorizationExpression.Reference("object", List.of("name")),
            new ObjectAuthorizationExpression.Literal("task")
        );
        assertThatCode(() ->
            ObjectAuthorizationExpressionValidator.validate(expression, schema)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects contains when the query field does not allow it")
    void shouldRejectContainsWhenFieldDoesNotAllowIt() {
        QuerySchemaView schema = schema(
            new QueryFieldDescriptor(
                QueryPath.parse("name"),
                TypeDescriptor.of(String.class),
                false,
                Set.of(QueryOperator.EQ)
            )
        );
        ObjectAuthorizationExpression expression = new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.CONTAINS,
            new ObjectAuthorizationExpression.Reference("object", List.of("name")),
            new ObjectAuthorizationExpression.Literal("task")
        );
        assertThatThrownBy(() ->
            ObjectAuthorizationExpressionValidator.validate(expression, schema)
        ).hasMessageContaining("operator is not supported for object path name");
    }

    @Test
    @DisplayName("removes duplicate object schema and policy cache contracts")
    void shouldRemoveDuplicateSchemaAndPolicyCacheContracts() {
        assertThatThrownBy(() ->
            Class.forName("io.taskmigo.authorization.object.ObjectAuthorizationSchema")
        ).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() ->
            Class.forName("io.taskmigo.authorization.request.application.service.StatementArtifactCache")
        ).isInstanceOf(ClassNotFoundException.class);
    }

    private static QuerySchemaView schema(QueryFieldDescriptor field) {
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return "test-object-authorization";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of(field);
            }
        };
    }
}

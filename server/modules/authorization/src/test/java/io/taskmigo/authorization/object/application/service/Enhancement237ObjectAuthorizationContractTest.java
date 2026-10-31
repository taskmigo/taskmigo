package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Enhancement237ObjectAuthorizationContractTest {

    @Test
    @DisplayName("validates object authorization against its dedicated schema including contains")
    void shouldValidateAgainstAuthorizationSchemaIncludingContains() {
        ObjectAuthorizationSchema<TestObject> schema = schema(
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
    @DisplayName("rejects contains when the authorization field does not allow it")
    void shouldRejectContainsWhenFieldDoesNotAllowIt() {
        ObjectAuthorizationSchema<TestObject> schema = schema(
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
    @DisplayName("keeps Statement artifacts uncached")
    void shouldRemovePolicyCacheContract() {
        assertThatThrownBy(() ->
            Class.forName("io.taskmigo.authorization.request.application.service.StatementArtifactCache")
        ).isInstanceOf(ClassNotFoundException.class);
    }

    private static ObjectAuthorizationSchema<TestObject> schema(QueryFieldDescriptor field) {
        return new ObjectAuthorizationSchema<>() {
            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of(field);
            }
        };
    }

    private static final class TestObject {}
}

package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryBindingResolverTest {

    private static final ResourceType TICKET = ResourceType.of("resource:ticket");
    private static final FieldId PRIORITY = FieldId.of("field:ticket:priority");

    /**
     * Verifies application integration selects the execution binding for the effective runtime schema.
     *
     * Given: one query contract with two schema fingerprints selected by provider-neutral context.
     * Expect: binding resolution follows the effective schema fingerprint instead of rejecting the duplicate Java type.
     */
    @Test
    @DisplayName("selects the query binding for the effective runtime schema")
    void shouldSelectQueryBindingWhenRuntimeSchemaDependsOnContext() {
        // Arrange
        ResourceSchema required = schema(false);
        ResourceSchema nullable = schema(true);
        QueryBinding<TestQuery> requiredBinding = binding(required);
        QueryBinding<TestQuery> nullableBinding = binding(nullable);
        ResourceSchemaResolver schemas = (type, context) ->
            Boolean.TRUE.equals(context.attributes().get("nullable")) ? nullable : required;
        QueryBindingResolver resolver = QueryBindingResolver.registered(
            List.of(requiredBinding, nullableBinding),
            schemas
        );

        // Act
        QueryBindingResolver.Resolution resolution = resolver.resolve(
            TestQuery.class,
            new SchemaContext(Map.of("nullable", true))
        );

        // Assert
        assertThat(resolution.schema()).isSameAs(nullable);
        assertThat(resolution.binding()).isSameAs(nullableBinding);
    }

    @Test
    @DisplayName("resolves the query binding by exact predicate identity")
    void shouldResolveQueryBindingWhenPredicateCarriesRuntimeIdentity() {
        ResourceSchema required = schema(false);
        ResourceSchema nullable = schema(true);
        QueryBinding<TestQuery> requiredBinding = binding(required);
        QueryBinding<TestQuery> nullableBinding = binding(nullable);
        QueryBindingResolver resolver = QueryBindingResolver.registered(
            List.of(requiredBinding, nullableBinding),
            (type, context) -> required
        );

        QueryBinding<?> resolved = resolver.resolve(TestQuery.class, nullableBinding.identity());

        assertThat(resolved).isSameAs(nullableBinding);
    }

    private static ResourceSchema schema(boolean nullable) {
        return ResourceSchema.of(
            TICKET,
            List.of(new Field(PRIORITY, FieldPath.parse("priority"), LanguageType.Scalar.NUMBER, nullable))
        );
    }

    private static QueryBinding<TestQuery> binding(ResourceSchema schema) {
        return new StaticQueryBinding<>(TestQuery.class, schema, List.of());
    }

    private static final class TestQuery {}
}

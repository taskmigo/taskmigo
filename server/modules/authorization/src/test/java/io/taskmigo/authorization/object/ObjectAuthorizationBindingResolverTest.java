package io.taskmigo.authorization.object;

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

class ObjectAuthorizationBindingResolverTest {

    private static final ResourceType TICKET = ResourceType.of("resource:ticket");
    private static final FieldId PRIORITY = FieldId.of("field:ticket:priority");

    /**
     * Verifies Object Authorization selects the execution binding for the effective runtime schema.
     *
     * Given: one application object contract with two schema fingerprints selected by provider-neutral context.
     * Expect: binding resolution follows the effective schema fingerprint instead of rejecting the duplicate Java type.
     */
    @Test
    @DisplayName("selects the object binding for the effective runtime schema")
    void shouldSelectObjectBindingWhenRuntimeSchemaDependsOnContext() {
        // Arrange
        ResourceSchema required = schema(false);
        ResourceSchema nullable = schema(true);
        ObjectAuthorizationBinding<TestObject> requiredBinding = binding(required);
        ObjectAuthorizationBinding<TestObject> nullableBinding = binding(nullable);
        ResourceSchemaResolver schemas = (type, context) ->
            Boolean.TRUE.equals(context.attributes().get("nullable")) ? nullable : required;
        ObjectAuthorizationBindingResolver resolver = ObjectAuthorizationBindingResolver.registered(
            List.of(requiredBinding, nullableBinding),
            schemas
        );

        // Act
        ObjectAuthorizationBinding<?> resolved = resolver.resolve(
            TestObject.class,
            new SchemaContext(Map.of("nullable", true))
        );

        // Assert
        assertThat(resolved).isSameAs(nullableBinding);
    }

    private static ResourceSchema schema(boolean nullable) {
        return ResourceSchema.of(
            TICKET,
            List.of(new Field(PRIORITY, FieldPath.parse("priority"), LanguageType.Scalar.NUMBER, nullable))
        );
    }

    private static ObjectAuthorizationBinding<TestObject> binding(ResourceSchema schema) {
        return new StaticObjectAuthorizationBinding<>(TestObject.class, schema, List.of());
    }

    private static final class TestObject {}
}

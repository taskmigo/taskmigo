package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.language.CompilerEnvironment;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/// Builds compiler root bindings from semantic authorization resources.
public final class AuthorizationEmbeddedLanguageSchemas {

    private static final ResourceSchema PRINCIPAL = schema(
        ResourceType.of("taskmigo:authorization:principal"),
        List.of(field("principal", "id", string(), false), field("principal", "username", string(), false))
    );
    private static final ResourceSchema REQUEST = dynamicRequestSchema();
    private static final ConcurrentMap<String, CompilerEnvironment> OBJECTS = new ConcurrentHashMap<>();

    private AuthorizationEmbeddedLanguageSchemas() {}

    /// Returns the reusable request roots required by authorization policies.
    public static CompilerEnvironment request() {
        return CompilerEnvironment.of(
            Map.of(
                "principal",
                new CompilerEnvironment.Root(PRINCIPAL, false),
                "request",
                new CompilerEnvironment.Root(REQUEST, false)
            )
        );
    }

    /// Returns cached object authorization roots for one logical resource contract.
    public static <Q> CompilerEnvironment object(ObjectAuthorizationBinding<Q> schema) {
        return OBJECTS.computeIfAbsent(schema.identity(), ignored -> environment(objectSchema(schema)));
    }

    private static CompilerEnvironment environment(ResourceSchema object) {
        ResourceSchemaResolver resolver = ResourceSchemaResolver.fixed(
            Map.of(PRINCIPAL.type(), PRINCIPAL, REQUEST.type(), REQUEST, object.type(), object)
        );
        return CompilerEnvironment.of(
            Map.of(
                "principal",
                new CompilerEnvironment.Root(resolver.resolve(PRINCIPAL.type(), SchemaContext.EMPTY), false),
                "request",
                new CompilerEnvironment.Root(resolver.resolve(REQUEST.type(), SchemaContext.EMPTY), false),
                "object",
                new CompilerEnvironment.Root(resolver.resolve(object.type(), SchemaContext.EMPTY), true)
            )
        );
    }

    private static <Q> ResourceSchema objectSchema(ObjectAuthorizationBinding<Q> schema) {
        return schema.resourceSchema();
    }

    private static Field field(String owner, String path, LanguageType type, boolean nullable) {
        return new Field(FieldId.of("field:" + owner + ":" + path), FieldPath.parse(path), type, nullable);
    }

    private static ResourceSchema schema(ResourceType type, Collection<Field> fields) {
        return ResourceSchema.of(type, fields);
    }

    private static ResourceSchema dynamicRequestSchema() {
        ResourceSchema base = schema(
            ResourceType.of("taskmigo:authorization:request"),
            List.of(field("request", "method", string(), false), field("request", "path", string(), false))
        );
        return new ResourceSchema() {
            @Override
            public ResourceType type() {
                return base.type();
            }

            @Override
            public Field resolve(FieldPath path) {
                if (path.segments().size() >= 2 && path.segments().getFirst().equals("pathVariables")) {
                    return field("request", path.text(), string(), false);
                }
                return base.resolve(path);
            }

            @Override
            public Collection<Field> fields() {
                return base.fields();
            }

            @Override
            public SchemaFingerprint fingerprint() {
                return base.fingerprint();
            }
        };
    }

    private static LanguageType string() {
        return LanguageType.Scalar.STRING;
    }
}

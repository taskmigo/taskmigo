package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageContract;
import io.taskmigo.language.LanguageType;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QuerySchemaView;
import java.time.temporal.TemporalAccessor;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/// Builds the consumer-owned Language schemas used by authorization.
public final class AuthorizationEmbeddedLanguageSchemas {

    private static final EnvironmentSchema REQUEST = new EnvironmentSchema(
        "taskmigo.authorization.request.0.3.2",
        Map.of(
            "principal",
            root(Map.of("id", string(), "username", string())),
            "request",
            root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString()))
        )
    );

    private AuthorizationEmbeddedLanguageSchemas() {}

    public static EnvironmentSchema request() {
        return REQUEST;
    }

    public static EnvironmentSchema object(List<? extends QuerySchemaView> schemas) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (QuerySchemaView schema : schemas) {
            for (QueryFieldDescriptor field : schema.fields()) {
                String first = field.path().segments().getFirst();
                fields.putIfAbsent(first, field.path().segments().size() == 1 ? field(field) : nested(schema, first));
            }
        }
        return new EnvironmentSchema(
            "taskmigo.authorization.object." +
                LanguageContract.VERSION +
                ":" +
                schemas.stream().map(QuerySchemaView::identity).sorted().toList(),
            Map.of(
                "principal",
                root(Map.of("id", string(), "username", string())),
                "request",
                root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString())),
                "object",
                root(fields)
            )
        );
    }

    public static EnvironmentSchema object(QuerySchemaView schema) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (QueryFieldDescriptor field : schema.fields()) {
            String first = field.path().segments().getFirst();
            fields.put(first, field.path().segments().size() == 1 ? field(field) : nested(schema, first));
        }
        return new EnvironmentSchema(
            "taskmigo.authorization.object." + LanguageContract.VERSION + ":" + schema.identity(),
            Map.of(
                "principal",
                root(Map.of("id", string(), "username", string())),
                "request",
                root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString())),
                "object",
                new EnvironmentSchema.Root(
                    new EnvironmentSchema.Field(
                        new LanguageType.StructuredType(schema.operation(), fields),
                        false,
                        true
                    ),
                    fields
                )
            )
        );
    }

    private static EnvironmentSchema.Field field(QueryFieldDescriptor field) {
        return new EnvironmentSchema.Field(languageType(field.type()), field.nullable(), true);
    }

    private static EnvironmentSchema.Field nested(QuerySchemaView schema, String prefix) {
        List<String> prefixSegments = List.of(prefix.split("\\."));
        Map<String, EnvironmentSchema.Field> children = new HashMap<>();
        for (QueryFieldDescriptor field : schema.fields()) {
            List<String> segments = field.path().segments();
            if (
                segments.size() > prefixSegments.size() &&
                segments.subList(0, prefixSegments.size()).equals(prefixSegments)
            ) {
                String child = segments.get(prefixSegments.size());
                children.put(
                    child,
                    segments.size() == prefixSegments.size() + 1 ? field(field) : nested(schema, prefix + "." + child)
                );
            }
        }
        return new EnvironmentSchema.Field(new LanguageType.StructuredType(prefix, children), false, true);
    }

    private static LanguageType languageType(TypeDescriptor type) {
        Class<?> raw = type.rawType();
        if (
            raw == String.class ||
            raw == UUID.class ||
            raw == Character.class ||
            raw == char.class ||
            raw.isEnum() ||
            TemporalAccessor.class.isAssignableFrom(raw)
        ) {
            return LanguageType.Scalar.STRING;
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) {
            return LanguageType.Scalar.NUMBER;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            TypeDescriptor elementType = type.typeArguments().isEmpty()
                ? TypeDescriptor.of(Object.class)
                : type.typeArguments().getFirst();
            return new LanguageType.ListType(languageType(elementType));
        }
        return new LanguageType.StructuredType(raw.getName(), Map.of());
    }

    private static EnvironmentSchema.Root root(Map<String, EnvironmentSchema.Field> fields) {
        return new EnvironmentSchema.Root(string(), fields);
    }

    private static EnvironmentSchema.Field string() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false);
    }

    private static EnvironmentSchema.Field dynamicString() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false, LanguageType.Scalar.STRING);
    }
}

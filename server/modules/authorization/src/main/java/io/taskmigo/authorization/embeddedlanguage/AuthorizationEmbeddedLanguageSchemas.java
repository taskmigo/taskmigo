package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageContract;
import io.taskmigo.language.LanguageType;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ResolvableType;

/// Builds the consumer-owned Language schemas used by authorization.
@SuppressWarnings({ "checkstyle:NeedBraces", "checkstyle:OverloadMethodsDeclarationOrder" })
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

    /// Returns the reusable request-only schema required by the authorization specification.
    public static EnvironmentSchema request() {
        return REQUEST;
    }

    /// Returns an object schema containing the fields registered by all logical object contracts.
    public static EnvironmentSchema object(List<? extends ObjectAuthorizationSchema<?>> schemas) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (ObjectAuthorizationSchema<?> schema : schemas) {
            for (ObjectAuthorizationField field : schema.fields()) {
                String first = field.path().segments().getFirst();
                fields.putIfAbsent(first, field.path().segments().size() == 1 ? field(field) : nested(schema, first));
            }
        }
        return new EnvironmentSchema(
            "taskmigo.authorization.object." +
                LanguageContract.VERSION +
                ":" +
                schemas.stream().map(ObjectAuthorizationSchema::identity).sorted().toList(),
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

    /// Returns an object schema derived from an authorization-owned logical object schema.
    public static <Q> EnvironmentSchema object(ObjectAuthorizationSchema<Q> schema) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (ObjectAuthorizationField field : schema.fields()) {
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
                        new LanguageType.StructuredType(schema.objectType().getName(), fields),
                        false,
                        true
                    ),
                    fields
                )
            )
        );
    }

    private static EnvironmentSchema.Field field(ObjectAuthorizationField field) {
        return new EnvironmentSchema.Field(languageType(field.type()), field.nullable(), true);
    }

    private static <Q> EnvironmentSchema.Field nested(ObjectAuthorizationSchema<Q> schema, String prefix) {
        List<String> prefixSegments = List.of(prefix.split("\\."));
        Map<String, EnvironmentSchema.Field> children = new HashMap<>();
        for (ObjectAuthorizationField field : schema.fields()) {
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

    private static LanguageType languageType(ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        if (
            raw == String.class || raw == UUID.class || raw == Character.class || raw == char.class
        ) return LanguageType.Scalar.STRING;
        if (raw == Boolean.class || raw == boolean.class) return LanguageType.Scalar.BOOL;
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) return LanguageType.Scalar.NUMBER;
        if (Collection.class.isAssignableFrom(raw)) return new LanguageType.ListType(languageType(type.getGeneric(0)));
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

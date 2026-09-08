package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.auth.authorization.object.AuthorizationObjectQueryDialect;
import io.taskmigo.auth.authorization.object.FilterSchema;
import io.taskmigo.embeddedlanguage.EnvironmentSchema;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QuerySchema;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ResolvableType;

/// Builds the consumer-owned Embedded Language schemas used by authorization.
@SuppressWarnings({ "checkstyle:NeedBraces", "checkstyle:OverloadMethodsDeclarationOrder" })
public final class AuthorizationEmbeddedLanguageSchemas {

    private AuthorizationEmbeddedLanguageSchemas() {}

    /// Returns the request-only schema required by the authorization specification.
    public static EnvironmentSchema request() {
        return new EnvironmentSchema(
            "taskmigo.authorization.request.0.3.2",
            Map.of(
                "principal",
                root(Map.of("id", string(), "username", string())),
                "request",
                root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString()))
            )
        );
    }

    /// Returns an object schema containing the fields registered by all dialects.
    public static EnvironmentSchema object(List<? extends AuthorizationObjectQueryDialect> dialects) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (AuthorizationObjectQueryDialect dialect : dialects) {
            dialect.fields().forEach((name, type) -> fields.putIfAbsent(name, field(name, type, dialect)));
        }
        return new EnvironmentSchema(
            "taskmigo.authorization.object.0.3.2",
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

    /// Returns an object schema for one concrete query dialect.
    public static EnvironmentSchema object(AuthorizationObjectQueryDialect dialect) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        dialect.fields().forEach((name, type) -> fields.put(name, field(name, type, dialect)));
        return new EnvironmentSchema(
            "taskmigo.authorization.object.0.3.2." + dialect.method() + dialect.path(),
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

    /// Returns an object schema derived from a persistence-neutral Query Schema.
    public static <Q> EnvironmentSchema object(QuerySchema<Q> schema) {
        Map<String, EnvironmentSchema.Field> fields = new HashMap<>();
        for (QueryField field : schema.fields()) {
            String first = field.path().segments().getFirst();
            fields.put(first, field.path().segments().size() == 1 ? field(field) : nested(schema, first));
        }
        return new EnvironmentSchema(
            "taskmigo.authorization.object.0.4.0:" + schema.identity(),
            Map.of(
                "principal", root(Map.of("id", string(), "username", string())),
                "request", root(Map.of("method", string(), "path", string(), "pathVariables", dynamicString())),
                "object", new EnvironmentSchema.Root(new EnvironmentSchema.Field(
                    new LanguageType.StructuredType(schema.queryType().getName(), fields), false, true
                ), fields)
            )
        );
    }

    private static <Q> EnvironmentSchema.Field nested(QuerySchema<Q> schema, String prefix) {
        List<String> prefixSegments = List.of(prefix.split("\\."));
        Map<String, EnvironmentSchema.Field> children = new HashMap<>();
        for (QueryField field : schema.fields()) {
            List<String> segments = field.path().segments();
            if (segments.size() > prefixSegments.size()
                && segments.subList(0, prefixSegments.size()).equals(prefixSegments)) {
                String child = segments.get(prefixSegments.size());
                children.put(child, segments.size() == prefixSegments.size() + 1
                    ? field(field) : nested(schema, prefix + "." + child));
            }
        }
        return new EnvironmentSchema.Field(new LanguageType.StructuredType(prefix, children), false, true);
    }

    private static EnvironmentSchema.Field field(QueryField field) {
        return new EnvironmentSchema.Field(languageType(field.type()), field.nullable(), true);
    }

    private static LanguageType languageType(ResolvableType type) {
        Class<?> raw = type.resolve(Object.class);
        if (raw == String.class || raw == UUID.class || raw == Character.class || raw == char.class) return LanguageType.Scalar.STRING;
        if (raw == Boolean.class || raw == boolean.class) return LanguageType.Scalar.BOOL;
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) return LanguageType.Scalar.NUMBER;
        if (Collection.class.isAssignableFrom(raw)) return new LanguageType.ListType(languageType(type.getGeneric(0)));
        return new LanguageType.StructuredType(raw.getName(), Map.of());
    }

    private static EnvironmentSchema.Root root(Map<String, EnvironmentSchema.Field> fields) {
        return new EnvironmentSchema.Root(string(), fields);
    }

    private static EnvironmentSchema.Field field(String name, Class<?> type, FilterSchema schema) {
        boolean nullable = schema.nullableFields().contains(name);
        return new EnvironmentSchema.Field(policyType(type), nullable, true);
    }

    private static EnvironmentSchema.Field string() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false);
    }

    private static EnvironmentSchema.Field dynamicString() {
        return new EnvironmentSchema.Field(LanguageType.Scalar.STRING, false, false, LanguageType.Scalar.STRING);
    }

    private static LanguageType policyType(Class<?> type) {
        if (type == String.class || type == UUID.class) {
            return LanguageType.Scalar.STRING;
        }
        if (type == Boolean.class || type == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (
            Number.class.isAssignableFrom(type) || (type.isPrimitive() && type != boolean.class && type != char.class)
        ) {
            return LanguageType.Scalar.NUMBER;
        }
        throw new IllegalArgumentException("unsupported authorization query field type: " + type.getName());
    }
}

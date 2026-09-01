package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationExpression.Literal;
import io.taskmigo.authorization.AuthorizationExpression.ValueType;
import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.FieldRule;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Statement;
import io.taskmigo.authorization.AuthorizationResource.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public final class AuthorizationCompiler {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");
    private static final Pattern FIELD_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private final AuthorizationExpressionCompiler expressions = new AuthorizationExpressionCompiler();
    private final List<AuthorizationObjectQueryDialect> objectQueryDialects;
    private final Map<UUID, CachedStatement> cache = new ConcurrentHashMap<>();

    public AuthorizationCompiler(List<AuthorizationObjectQueryDialect> objectQueryDialects) {
        this.objectQueryDialects = List.copyOf(objectQueryDialects);
    }

    CompiledStatement compile(Statement resource, Origin origin) {
        String key = required(resource.key(), "key");
        if (!KEY_PATTERN.matcher(key).matches()) throw new IllegalArgumentException("Invalid authorization resource key: " + key);
        if (resource.match() == null) throw new IllegalArgumentException("match is required");
        String method = required(resource.match().method(), "match.method").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        if (!resource.match().method().equals(method)) throw new IllegalArgumentException("match.method must use canonical uppercase form");
        SafePathPattern path = SafePathPattern.compile(required(resource.match().path(), "match.path"));
        if (!path.source().startsWith("/")) throw new IllegalArgumentException("match.path must match an absolute API path");
        if (resource.target() == null) throw new IllegalArgumentException("target is required");

        List<FieldRule> fields = resource.fields();
        if (resource.target() == Target.REQUEST) {
            if (resource.effect() == null) throw new IllegalArgumentException("target: request requires effect");
            if (!fields.isEmpty()) throw new IllegalArgumentException("target: request must not contain fields");
        } else if (resource.effect() == null && fields.isEmpty()) {
            throw new IllegalArgumentException("target: object requires effect or at least one field rule");
        }

        AuthorizationExpression condition = condition(resource.when(), resource.target() == Target.OBJECT);
        if (resource.target() == Target.OBJECT && resource.effect() != null) {
            validateObjectPredicate(method, path, condition);
        }
        List<CompiledFieldRule> compiledFields = new ArrayList<>(fields.size());
        for (FieldRule field : fields) {
            if (field.effect() == null) throw new IllegalArgumentException("Field rule effect is required");
            if (field.names() == null || field.names().isEmpty()) throw new IllegalArgumentException("Field rule requires at least one field name");
            List<String> names = field.names().stream().map(name -> fieldName(name)).distinct().toList();
            compiledFields.add(new CompiledFieldRule(field.effect(), names, condition(field.when(), true)));
        }

        Effect effect = resource.effect() == null ? Effect.ALLOW : resource.effect();
        return new CompiledStatement(
            key,
            method,
            path,
            resource.target(),
            effect,
            resource.effect() != null,
            condition,
            compiledFields,
            origin
        );
    }

    CompiledStatement compileCached(UUID id, Statement resource, Origin origin) {
        return this.cache
            .compute(
                id,
                (ignored, current) -> current != null && current.matches(resource, origin)
                    ? current
                    : new CachedStatement(resource, origin, compile(resource, origin))
            )
            .compiled();
    }

    void invalidate(UUID id) {
        this.cache.remove(id);
    }

    private void validateObjectPredicate(String method, SafePathPattern path, AuthorizationExpression condition) {
        List<AuthorizationObjectQueryDialect> matching = this.objectQueryDialects
            .stream()
            .filter(dialect -> dialect.method().equals(method) && path.matches(dialect.path()))
            .toList();
        if (matching.isEmpty()) {
            throw new IllegalArgumentException(
                "No database authorization query dialect supports object Statement matcher " + method + " " + path.source()
            );
        }
        matching.forEach(dialect -> dialect.validate(condition));
    }

    private AuthorizationExpression condition(@Nullable String source, boolean objectAllowed) {
        if (source == null || source.isBlank()) return new Literal(Boolean.TRUE, ValueType.BOOLEAN);
        return this.expressions.compile(source, objectAllowed);
    }

    private static String fieldName(String value) {
        String name = required(value, "fields.names[]");
        if (!FIELD_PATTERN.matcher(name).matches()) throw new IllegalArgumentException("Invalid authorization field name: " + name);
        return name;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must be a non-blank string");
        return value;
    }

    private record CachedStatement(Statement resource, Origin origin, CompiledStatement compiled) {
        boolean matches(Statement candidate, Origin candidateOrigin) {
            return this.origin == candidateOrigin && this.resource.equals(candidate);
        }
    }
}

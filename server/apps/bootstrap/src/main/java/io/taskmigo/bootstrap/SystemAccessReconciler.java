package io.taskmigo.bootstrap;

import io.taskmigo.access.AccessService;
import io.taskmigo.access.AccessService.SystemRoleDefinition;
import io.taskmigo.access.AccessService.SystemStatementDefinition;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.dataformat.yaml.YAMLMapper;

/// Reconciles compact bootstrap-owned Statement and Role YAML into PostgreSQL.
@Component
final class SystemAccessReconciler implements ApplicationRunner {

    private static final String STATEMENT_PATTERN = "classpath*:system/access/statements/*.yaml";
    private static final String ROLE_PATTERN = "classpath*:system/access/roles/*.yaml";

    private final AccessService access;
    private final YAMLMapper yaml = YAMLMapper.builder().build();

    SystemAccessReconciler(AccessService access) {
        this.access = access;
    }

    @Override
    public void run(ApplicationArguments arguments) throws IOException {
        this.reconcile();
    }

    void reconcile() throws IOException {
        Map<String, SystemStatementDefinition> statements = new LinkedHashMap<>();
        for (Resource resource : resources(STATEMENT_PATTERN)) {
            LoadedStatement statement = statement(this.read(resource), resource.getDescription());
            if (
                statements.putIfAbsent(
                    statement.key(),
                    new SystemStatementDefinition(statement.name(), statement.description(), statement.definition())
                ) != null
            ) {
                throw new IllegalArgumentException("Duplicate system Statement key: " + statement.key());
            }
        }

        Map<String, SystemRoleDefinition> roles = new LinkedHashMap<>();
        for (Resource resource : resources(ROLE_PATTERN)) {
            LoadedRole role = role(this.read(resource), resource.getDescription());
            if (
                roles.putIfAbsent(
                    role.key(),
                    new SystemRoleDefinition(role.name(), role.description(), role.statementKeys())
                ) != null
            ) {
                throw new IllegalArgumentException("Duplicate system Role key: " + role.key());
            }
        }
        this.access.reconcileSystem(statements, roles);
    }

    private Map<String, Object> read(Resource resource) throws IOException {
        return this.yaml.readValue(resource.getInputStream(), new TypeReference<Map<String, Object>>() {});
    }

    private static Resource[] resources(String pattern) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
        if (resources.length == 0) throw new IllegalStateException("No system access definitions found at " + pattern);
        Arrays.sort(resources, java.util.Comparator.comparing(Resource::getDescription));
        return resources;
    }

    private static LoadedStatement statement(Map<String, Object> source, String description) {
        String key = string(required(source, "key"), "key");
        String name = displayName(source, key);
        String mode = mode(source, description);
        Map<String, Object> rule = map(required(source, mode), mode);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put(
            "methods",
            strings(required(rule, "methods"), mode + ".methods")
                .stream()
                .map(method -> method.toUpperCase(Locale.ROOT))
                .toList()
        );
        target.put("path", string(required(rule, "path"), mode + ".path"));

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("mode", mode);
        spec.put("target", target);
        spec.put("effect", effect(rule.get("effect"), mode + ".effect"));
        spec.put("when", map(required(rule, "when"), mode + ".when"));
        if ("response".equals(mode) && rule.get("fields") != null) {
            spec.put("fields", Map.of("allow", strings(rule.get("fields"), "response.fields")));
        }
        if ("request".equals(mode) && rule.get("fields") != null) {
            throw new IllegalArgumentException("request.fields is not supported: " + description);
        }

        return new LoadedStatement(
            key,
            name,
            nullableString(source.get("description"), "description", description),
            Map.of("kind", "acl/statement", "spec", spec)
        );
    }

    private static LoadedRole role(Map<String, Object> source, String description) {
        String key = string(required(source, "key"), "key");
        Set<String> statementKeys = new LinkedHashSet<>(strings(required(source, "statements"), "statements"));
        return new LoadedRole(
            key,
            displayName(source, key),
            nullableString(source.get("description"), "description", description),
            Set.copyOf(statementKeys)
        );
    }

    private static String mode(Map<String, Object> source, String description) {
        boolean request = source.get("request") != null;
        boolean response = source.get("response") != null;
        if (request == response) {
            throw new IllegalArgumentException(
                "Statement must define exactly one of request or response: " + description
            );
        }
        return request ? "request" : "response";
    }

    private static String displayName(Map<String, Object> source, String key) {
        Object value = source.get("name");
        return value == null ? key : string(value, "name");
    }

    private static String effect(@Nullable Object value, String field) {
        String effect = value == null ? "allow" : string(value, field).toLowerCase(Locale.ROOT);
        if (!Set.of("allow", "deny").contains(effect)) {
            throw new IllegalArgumentException(field + " must be allow or deny");
        }
        return effect;
    }

    private static Object required(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) throw new IllegalArgumentException("Missing system access field: " + key);
        return value;
    }

    private static Map<String, Object> map(Object value, String field) {
        if (!(value instanceof Map<?, ?> raw)) throw new IllegalArgumentException(field + " must be an object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException(
                field + " keys must be strings"
            );
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<String> strings(Object value, String field) {
        if (value instanceof String string) return List.of(string(string, field));
        if (!(value instanceof List<?> raw)) throw new IllegalArgumentException(field + " must be a string or array");
        return raw
            .stream()
            .map(item -> string(item, field + "[]"))
            .toList();
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return string.trim();
    }

    private static @Nullable String nullableString(@Nullable Object value, String field, String source) {
        if (value == null) return null;
        if (!(value instanceof String string)) throw new IllegalArgumentException(
            field + " must be a string: " + source
        );
        return string.isBlank() ? null : string.trim();
    }

    private record LoadedStatement(
        String key,
        String name,
        @Nullable String description,
        Map<String, Object> definition
    ) {}

    private record LoadedRole(String key, String name, @Nullable String description, Set<String> statementKeys) {}
}

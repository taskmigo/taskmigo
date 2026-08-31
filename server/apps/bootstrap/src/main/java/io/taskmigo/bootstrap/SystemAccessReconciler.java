package io.taskmigo.bootstrap;

import io.taskmigo.access.AccessService;
import io.taskmigo.access.AccessService.SystemRoleDefinition;
import io.taskmigo.access.AccessService.SystemStatementDefinition;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

/// Reconciles bootstrap-owned reusable Statements and built-in Roles from YAML into PostgreSQL.
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
        for (var entry : this.load(STATEMENT_PATTERN, "acl/statement").entrySet()) {
            Metadata metadata = metadata(entry.getValue(), entry.getKey());
            statements.put(
                metadata.key(),
                new SystemStatementDefinition(metadata.name(), metadata.description(), entry.getValue())
            );
        }

        Map<String, SystemRoleDefinition> roles = new LinkedHashMap<>();
        for (var entry : this.load(ROLE_PATTERN, "acl/role").entrySet()) {
            Metadata metadata = metadata(entry.getValue(), entry.getKey());
            Map<String, Object> spec = map(required(entry.getValue(), "spec"), "spec");
            Set<String> statementKeys = list(required(spec, "statements"), "spec.statements")
                .stream()
                .map(value -> string(value, "spec.statements[]"))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            roles.put(metadata.key(), new SystemRoleDefinition(metadata.name(), metadata.description(), statementKeys));
        }
        this.access.reconcileSystem(statements, roles);
    }

    private Map<String, Map<String, Object>> load(String pattern, String expectedKind) throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
        if (resources.length == 0) throw new IllegalStateException("No system access definitions found at " + pattern);
        Arrays.sort(resources, java.util.Comparator.comparing(Resource::getDescription));

        Map<String, Map<String, Object>> definitions = new LinkedHashMap<>();
        for (Resource resource : resources) {
            Map<String, Object> definition = this.yaml.readValue(
                resource.getInputStream(),
                new TypeReference<Map<String, Object>>() {}
            );
            String kind = string(required(definition, "kind"), "kind");
            if (!expectedKind.equals(kind)) throw new IllegalArgumentException(
                "Expected " + expectedKind + " in " + resource.getDescription() + ", got " + kind
            );
            Metadata metadata = metadata(definition, resource.getDescription());
            if (definitions.putIfAbsent(metadata.key(), definition) != null) {
                throw new IllegalArgumentException("Duplicate system access key: " + metadata.key());
            }
        }
        return definitions;
    }

    private static Metadata metadata(Map<String, Object> definition, String source) {
        Map<String, Object> metadata = map(required(definition, "metadata"), "metadata");
        return new Metadata(
            string(required(metadata, "key"), "metadata.key"),
            string(required(metadata, "name"), "metadata.name"),
            nullableString(metadata.get("description"), "metadata.description", source)
        );
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
            if (!(entry.getKey() instanceof String key)) throw new IllegalArgumentException(field + " keys must be strings");
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<Object> list(Object value, String field) {
        if (!(value instanceof List<?> raw)) throw new IllegalArgumentException(field + " must be an array");
        return List.copyOf(raw);
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return string.trim();
    }

    private static @Nullable String nullableString(@Nullable Object value, String field, String source) {
        if (value == null) return null;
        if (!(value instanceof String string)) throw new IllegalArgumentException(field + " must be a string: " + source);
        return string.isBlank() ? null : string.trim();
    }

    private record Metadata(String key, String name, @Nullable String description) {}
}

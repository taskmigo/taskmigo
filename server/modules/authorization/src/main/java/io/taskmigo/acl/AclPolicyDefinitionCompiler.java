package io.taskmigo.acl;

import io.taskmigo.acl.AclExpression.All;
import io.taskmigo.acl.AclExpression.Any;
import io.taskmigo.acl.AclExpression.Eq;
import io.taskmigo.acl.AclExpression.Exists;
import io.taskmigo.acl.AclExpression.Literal;
import io.taskmigo.acl.AclExpression.Not;
import io.taskmigo.acl.AclExpression.Ref;
import io.taskmigo.acl.AclExpression.Relation;
import io.taskmigo.acl.AclExpression.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Compiles the JSON object accepted by the ACL management API into the restricted, database-translatable ACL AST.
public final class AclPolicyDefinitionCompiler {

    public String kind(Map<String, Object> definition) {
        return string(required(definition, "kind"), "kind");
    }

    public RequestAclPolicy compileRequest(String name, RequestAclPolicy.Origin origin, Map<String, Object> definition) {
        requireKind(definition, "acl/request");
        Map<String, Object> spec = map(required(definition, "spec"), "spec");
        return new RequestAclPolicy(name, origin, target(spec), requestRules(spec));
    }

    public ResponseAclPolicy compileResponse(
        String name,
        ResponseAclPolicy.Origin origin,
        Map<String, Object> definition
    ) {
        requireKind(definition, "acl/response");
        Map<String, Object> spec = map(required(definition, "spec"), "spec");
        return new ResponseAclPolicy(name, origin, target(spec), responseRules(spec));
    }

    private static ApiTarget target(Map<String, Object> spec) {
        Map<String, Object> target = map(required(spec, "target"), "spec.target");
        Set<String> methods = list(required(target, "methods"), "spec.target.methods")
            .stream()
            .map(value -> string(value, "spec.target.methods[]"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ApiTarget(methods, string(required(target, "path"), "spec.target.path"));
    }

    private static List<RequestAclPolicy.Rule> requestRules(Map<String, Object> spec) {
        Map<String, Object> rules = map(required(spec, "rules"), "spec.rules");
        List<RequestAclPolicy.Rule> compiled = new ArrayList<>();
        for (var entry : rules.entrySet()) {
            Map<String, Object> rule = map(entry.getValue(), "spec.rules." + entry.getKey());
            compiled.add(
                new RequestAclPolicy.Rule(
                    entry.getKey(),
                    RequestAclPolicy.Effect.valueOf(
                        string(required(rule, "effect"), "effect").toUpperCase(Locale.ROOT)
                    ),
                    expression(map(required(rule, "when"), "when"))
                )
            );
        }
        return List.copyOf(compiled);
    }

    private static List<ResponseAclPolicy.Rule> responseRules(Map<String, Object> spec) {
        Map<String, Object> rules = map(required(spec, "rules"), "spec.rules");
        List<ResponseAclPolicy.Rule> compiled = new ArrayList<>();
        for (var entry : rules.entrySet()) {
            Map<String, Object> rule = map(entry.getValue(), "spec.rules." + entry.getKey());
            compiled.add(
                new ResponseAclPolicy.Rule(
                    entry.getKey(),
                    ResponseAclPolicy.Effect.valueOf(
                        string(required(rule, "effect"), "effect").toUpperCase(Locale.ROOT)
                    ),
                    expression(map(required(rule, "when"), "when")),
                    fields(rule.get("fields"))
                )
            );
        }
        return List.copyOf(compiled);
    }

    private static ResponseAclPolicy.FieldSelection fields(Object raw) {
        if (raw == null) return ResponseAclPolicy.FieldSelection.allFields();
        Map<String, Object> fields = map(raw, "fields");
        Object allow = fields.get("allow");
        if (allow == null) return ResponseAclPolicy.FieldSelection.allFields();
        Set<String> names = list(allow, "fields.allow")
            .stream()
            .map(value -> string(value, "fields.allow[]"))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return ResponseAclPolicy.FieldSelection.only(names);
    }

    private static AclExpression expression(Map<String, Object> node) {
        if (node.size() != 1) throw new IllegalArgumentException("ACL expression must contain exactly one operator");
        var entry = node.entrySet().getFirst();
        return switch (entry.getKey()) {
            case "eq" -> {
                List<Object> operands = list(entry.getValue(), "eq");
                if (operands.size() != 2) throw new IllegalArgumentException("eq requires exactly two operands");
                yield new Eq(value(operands.get(0)), value(operands.get(1)));
            }
            case "exists" -> new Exists(value(entry.getValue()));
            case "all" -> new All(expressions(entry.getValue(), "all"));
            case "any" -> new Any(expressions(entry.getValue(), "any"));
            case "not" -> new Not(expression(map(entry.getValue(), "not")));
            case "relation" -> {
                Map<String, Object> relation = map(entry.getValue(), "relation");
                yield new Relation(
                    string(required(relation, "name"), "relation.name"),
                    value(required(relation, "principal")),
                    value(required(relation, "object"))
                );
            }
            default -> throw new IllegalArgumentException("Unsupported ACL operator: " + entry.getKey());
        };
    }

    private static List<AclExpression> expressions(Object raw, String field) {
        return list(raw, field).stream().map(value -> expression(map(value, field + "[]"))).toList();
    }

    private static Value value(Object raw) {
        if (raw instanceof String string && (
            string.startsWith("principal.") || string.startsWith("request.") || string.startsWith("object.")
        )) {
            return new Ref(string);
        }
        return new Literal(raw);
    }

    private static void requireKind(Map<String, Object> definition, String expected) {
        String actual = string(required(definition, "kind"), "kind");
        if (!expected.equals(actual)) throw new IllegalArgumentException("Expected kind " + expected + ", got " + actual);
    }

    private static Object required(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) throw new IllegalArgumentException("Missing ACL field: " + key);
        return value;
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String string) || string.isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return string;
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
}

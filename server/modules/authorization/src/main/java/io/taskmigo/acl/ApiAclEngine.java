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
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ApiAclEngine {

    public boolean isRequestAllowed(
        List<RequestAclPolicy> policies,
        String method,
        String path,
        Map<String, Object> context
    ) {
        List<RequestAclPolicy> matching = policies.stream().filter(policy -> policy.target().matches(method, path)).toList();
        List<RequestAclPolicy> system = matching.stream()
            .filter(policy -> policy.origin() == RequestAclPolicy.Origin.SYSTEM)
            .toList();
        List<RequestAclPolicy> custom = matching.stream()
            .filter(policy -> policy.origin() == RequestAclPolicy.Origin.CUSTOM)
            .toList();

        if (system.stream().anyMatch(policy -> !policyAllows(policy.rules(), context))) return false;
        if (custom.isEmpty()) return true;
        if (custom.stream().flatMap(policy -> policy.rules().stream()).anyMatch(rule ->
            rule.effect() == RequestAclPolicy.Effect.DENY && evaluate(rule.when(), context)
        )) return false;

        boolean hasAllow = custom.stream()
            .flatMap(policy -> policy.rules().stream())
            .anyMatch(rule -> rule.effect() == RequestAclPolicy.Effect.ALLOW);
        return !hasAllow || custom.stream().flatMap(policy -> policy.rules().stream()).anyMatch(rule ->
            rule.effect() == RequestAclPolicy.Effect.ALLOW && evaluate(rule.when(), context)
        );
    }

    public ResponsePlan planResponse(
        List<ResponseAclPolicy> policies,
        String method,
        String path,
        Map<String, Object> context
    ) {
        List<ResponseAclPolicy> matching = policies.stream().filter(policy -> policy.target().matches(method, path)).toList();
        List<ResponseAclPolicy.Rule> system = matching.stream()
            .filter(policy -> policy.origin() == ResponseAclPolicy.Origin.SYSTEM)
            .flatMap(policy -> policy.rules().stream())
            .toList();
        List<ResponseAclPolicy.Rule> custom = matching.stream()
            .filter(policy -> policy.origin() == ResponseAclPolicy.Origin.CUSTOM)
            .flatMap(policy -> policy.rules().stream())
            .toList();

        List<AclExpression> predicates = new ArrayList<>();
        system.stream()
            .filter(rule -> rule.effect() == ResponseAclPolicy.Effect.ALLOW)
            .map(ResponseAclPolicy.Rule::when)
            .map(expression -> specialize(expression, context))
            .forEach(predicates::add);
        system.stream()
            .filter(rule -> rule.effect() == ResponseAclPolicy.Effect.DENY)
            .map(ResponseAclPolicy.Rule::when)
            .map(expression -> new Not(specialize(expression, context)))
            .forEach(predicates::add);

        List<AclExpression> customAllows = custom.stream()
            .filter(rule -> rule.effect() == ResponseAclPolicy.Effect.ALLOW)
            .map(ResponseAclPolicy.Rule::when)
            .map(expression -> specialize(expression, context))
            .toList();
        if (!customAllows.isEmpty()) predicates.add(new Any(customAllows));

        custom.stream()
            .filter(rule -> rule.effect() == ResponseAclPolicy.Effect.DENY)
            .map(ResponseAclPolicy.Rule::when)
            .map(expression -> new Not(specialize(expression, context)))
            .forEach(predicates::add);

        ResponseAclPolicy.FieldSelection fields = ResponseAclPolicy.FieldSelection.allFields();
        for (ResponseAclPolicy.Rule rule : system) {
            if (rule.effect() == ResponseAclPolicy.Effect.ALLOW) fields = fields.intersect(rule.fields());
        }
        for (ResponseAclPolicy.Rule rule : custom) {
            if (rule.effect() == ResponseAclPolicy.Effect.ALLOW) fields = fields.intersect(rule.fields());
        }

        return new ResponsePlan(new All(predicates), fields);
    }

    private static boolean policyAllows(List<RequestAclPolicy.Rule> rules, Map<String, Object> context) {
        if (rules.stream().anyMatch(rule -> rule.effect() == RequestAclPolicy.Effect.DENY && evaluate(rule.when(), context))) {
            return false;
        }
        return rules.stream().anyMatch(rule -> rule.effect() == RequestAclPolicy.Effect.ALLOW && evaluate(rule.when(), context));
    }

    private static boolean evaluate(AclExpression expression, Map<String, Object> context) {
        return switch (expression) {
            case Eq(var left, var right) -> java.util.Objects.equals(resolve(left, context), resolve(right, context));
            case Exists(var value) -> resolve(value, context) != null;
            case All(var expressions) -> expressions.stream().allMatch(item -> evaluate(item, context));
            case Any(var expressions) -> expressions.stream().anyMatch(item -> evaluate(item, context));
            case Not(var item) -> !evaluate(item, context);
            case Relation ignored -> throw new IllegalArgumentException("Relations are response-only in the POC");
        };
    }

    private static AclExpression specialize(AclExpression expression, Map<String, Object> context) {
        return switch (expression) {
            case Eq(var left, var right) -> new Eq(specialize(left, context), specialize(right, context));
            case Exists(var value) -> new Exists(specialize(value, context));
            case All(var expressions) -> new All(expressions.stream().map(item -> specialize(item, context)).toList());
            case Any(var expressions) -> new Any(expressions.stream().map(item -> specialize(item, context)).toList());
            case Not(var item) -> new Not(specialize(item, context));
            case Relation(var name, var principal, var object) -> new Relation(
                name,
                specialize(principal, context),
                specialize(object, context)
            );
        };
    }

    private static Value specialize(Value value, Map<String, Object> context) {
        if (value instanceof Ref(var path) && !path.startsWith("object.")) return new Literal(context.get(path));
        return value;
    }

    private static @Nullable Object resolve(Value value, Map<String, Object> context) {
        return switch (value) {
            case Literal(var literal) -> literal;
            case Ref(var path) -> context.get(path);
        };
    }

    public record ResponsePlan(AclExpression objectPredicate, ResponseAclPolicy.FieldSelection fields) {}
}

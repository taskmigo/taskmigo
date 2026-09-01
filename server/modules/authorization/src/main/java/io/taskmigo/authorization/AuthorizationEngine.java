package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationDecision.FieldTrace;
import io.taskmigo.authorization.AuthorizationDecision.Outcome;
import io.taskmigo.authorization.AuthorizationExpression.Binary;
import io.taskmigo.authorization.AuthorizationExpression.BinaryOperator;
import io.taskmigo.authorization.AuthorizationExpression.Literal;
import io.taskmigo.authorization.AuthorizationExpression.ValueType;
import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.Target;
import io.taskmigo.authorization.EffectiveAuthorization.EffectiveStatement;
import io.taskmigo.authorization.EffectiveAuthorization.Provenance;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class AuthorizationEngine {

    private final AuthorizationEvaluator evaluator = new AuthorizationEvaluator();

    public AuthorizationDecision authorizeRequest(
        EffectiveAuthorization authorization,
        String method,
        String normalizedPath,
        Map<String, Object> context
    ) {
        List<EffectiveStatement> candidates = candidates(authorization, method, normalizedPath, Target.REQUEST);
        List<String> matched = new ArrayList<>();
        List<String> allows = new ArrayList<>();
        List<String> denies = new ArrayList<>();
        Map<String, List<Provenance>> provenance = new LinkedHashMap<>();
        for (EffectiveStatement effective : candidates) {
            CompiledStatement statement = effective.statement();
            if (!this.evaluator.test(statement.condition(), context)) continue;
            matched.add(statement.key());
            provenance.put(statement.key(), effective.provenance());
            (statement.effect() == Effect.DENY ? denies : allows).add(statement.key());
        }
        Outcome outcome = !denies.isEmpty() ? Outcome.DENY : !allows.isEmpty() ? Outcome.ALLOW : Outcome.DENY;
        return new AuthorizationDecision(outcome, Target.REQUEST, matched, allows, denies, provenance, null, List.of());
    }

    public ObjectPlan planObjects(
        EffectiveAuthorization authorization,
        String method,
        String normalizedPath,
        Map<String, Object> context
    ) {
        List<EffectiveStatement> candidates = candidates(authorization, method, normalizedPath, Target.OBJECT);
        List<AuthorizationExpression> allows = new ArrayList<>();
        List<AuthorizationExpression> denies = new ArrayList<>();
        List<String> matched = new ArrayList<>();
        List<String> allowKeys = new ArrayList<>();
        List<String> denyKeys = new ArrayList<>();
        List<PlannedFieldRule> fields = new ArrayList<>();
        Map<String, List<Provenance>> provenance = new LinkedHashMap<>();

        for (EffectiveStatement effective : candidates) {
            CompiledStatement statement = effective.statement();
            matched.add(statement.key());
            provenance.put(statement.key(), effective.provenance());
            AuthorizationExpression condition = this.evaluator.specialize(statement.condition(), context);
            if (statement.hasTopLevelEffect()) {
                (statement.effect() == Effect.DENY ? denies : allows).add(condition);
                (statement.effect() == Effect.DENY ? denyKeys : allowKeys).add(statement.key());
            }
            for (CompiledFieldRule field : statement.fields()) {
                fields.add(
                    new PlannedFieldRule(
                        statement.key(),
                        field.effect(),
                        field.names(),
                        this.evaluator.specialize(field.condition(), context),
                        effective.provenance()
                    )
                );
            }
        }

        AuthorizationExpression predicate = and(or(allows), not(or(denies)));
        List<FieldTrace> fieldTrace = fields
            .stream()
            .map(field -> new FieldTrace(field.statementKey(), field.names(), field.effect()))
            .toList();
        AuthorizationDecision decision = new AuthorizationDecision(
            Outcome.PLANNED,
            Target.OBJECT,
            matched,
            allowKeys,
            denyKeys,
            provenance,
            predicate,
            fieldTrace
        );
        return new ObjectPlan(predicate, fields, decision);
    }

    public FieldDecision authorizeFields(ObjectPlan plan, Map<String, Object> objectValues, Set<String> responseFields) {
        Map<String, Object> values = new LinkedHashMap<>(objectValues);
        Set<String> hidden = new LinkedHashSet<>();
        Map<String, List<String>> deniedBy = new LinkedHashMap<>();
        for (String field : responseFields) {
            List<String> matchingDenies = new ArrayList<>();
            for (PlannedFieldRule rule : plan.fields()) {
                if (rule.effect() != Effect.DENY || !rule.names().contains(field)) continue;
                if (this.evaluator.test(rule.condition(), values)) matchingDenies.add(rule.statementKey());
            }
            if (!matchingDenies.isEmpty()) {
                hidden.add(field);
                deniedBy.put(field, List.copyOf(matchingDenies));
            }
        }
        return new FieldDecision(Set.copyOf(hidden), Map.copyOf(deniedBy));
    }

    private static List<EffectiveStatement> candidates(
        EffectiveAuthorization authorization,
        String method,
        String path,
        Target target
    ) {
        return authorization
            .forMethod(method)
            .stream()
            .filter(item -> item.statement().target() == target && item.statement().path().matches(path))
            .toList();
    }

    private static AuthorizationExpression or(List<AuthorizationExpression> expressions) {
        if (expressions.isEmpty()) return new Literal(Boolean.FALSE, ValueType.BOOLEAN);
        return expressions.stream().reduce((left, right) -> new Binary(BinaryOperator.OR, left, right)).orElseThrow();
    }

    private static AuthorizationExpression and(AuthorizationExpression left, AuthorizationExpression right) {
        return new Binary(BinaryOperator.AND, left, right);
    }

    private static AuthorizationExpression not(AuthorizationExpression expression) {
        return new AuthorizationExpression.Unary(AuthorizationExpression.UnaryOperator.NOT, expression);
    }

    public record ObjectPlan(
        AuthorizationExpression predicate,
        List<PlannedFieldRule> fields,
        AuthorizationDecision decision
    ) {
        public ObjectPlan {
            fields = List.copyOf(fields);
        }
    }

    public record PlannedFieldRule(
        String statementKey,
        Effect effect,
        List<String> names,
        AuthorizationExpression condition,
        List<Provenance> provenance
    ) {
        public PlannedFieldRule {
            names = List.copyOf(names);
            provenance = List.copyOf(provenance);
        }
    }

    public record FieldDecision(Set<String> hiddenFields, Map<String, List<String>> deniedBy) {}
}

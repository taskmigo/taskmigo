package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationResource.Target;
import io.taskmigo.authorization.EffectiveAuthorization.Provenance;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Captures one authorization outcome and the Statements and provenance that produced it.
public record AuthorizationDecision(
    Outcome outcome,
    Target target,
    List<String> matchedStatements,
    List<String> allowedBy,
    List<String> deniedBy,
    Map<String, List<Provenance>> provenance,
    @Nullable AuthorizationExpression objectPredicatePlan,
    List<FieldTrace> fieldPlan
) {
    public AuthorizationDecision {
        matchedStatements = List.copyOf(matchedStatements);
        allowedBy = List.copyOf(allowedBy);
        deniedBy = List.copyOf(deniedBy);
        provenance = Map.copyOf(provenance);
        fieldPlan = List.copyOf(fieldPlan);
    }

    public enum Outcome {
        ALLOW,
        DENY,
        PLANNED,
    }

    public record FieldTrace(String statement, List<String> fields, AuthorizationResource.Effect effect) {
        public FieldTrace {
            fields = List.copyOf(fields);
        }
    }
}

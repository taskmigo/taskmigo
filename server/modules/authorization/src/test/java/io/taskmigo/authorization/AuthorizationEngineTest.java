package io.taskmigo.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.AuthorizationDecision.Outcome;
import io.taskmigo.authorization.AuthorizationExpression.Literal;
import io.taskmigo.authorization.AuthorizationExpression.ValueType;
import io.taskmigo.authorization.AuthorizationResource.Effect;
import io.taskmigo.authorization.AuthorizationResource.Origin;
import io.taskmigo.authorization.AuthorizationResource.Target;
import io.taskmigo.authorization.EffectiveAuthorization.EffectiveStatement;
import io.taskmigo.authorization.EffectiveAuthorization.Provenance;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthorizationEngineTest {

    private static final Literal TRUE = new Literal(Boolean.TRUE, ValueType.BOOLEAN);
    private final AuthorizationEngine engine = new AuthorizationEngine();

    @Test
    void denyOverridesAllowForRequest() {
        EffectiveAuthorization authorization = authorization(
            statement("allow", Target.REQUEST, Effect.ALLOW, true, TRUE, List.of()),
            statement("deny", Target.REQUEST, Effect.DENY, true, TRUE, List.of())
        );

        AuthorizationDecision decision = this.engine.authorizeRequest(
            authorization,
            "GET",
            "/api/v0/projects",
            Map.of()
        );

        assertThat(decision.outcome()).isEqualTo(Outcome.DENY);
        assertThat(decision.allowedBy()).containsExactly("allow");
        assertThat(decision.deniedBy()).containsExactly("deny");
        assertThat(decision.provenance()).containsKeys("allow", "deny");
    }

    @Test
    void requestFailsClosedWithoutMatchingAllow() {
        AuthorizationDecision decision = this.engine.authorizeRequest(
            new EffectiveAuthorization(List.of()),
            "GET",
            "/api/v0/projects",
            Map.of()
        );

        assertThat(decision.outcome()).isEqualTo(Outcome.DENY);
        assertThat(decision.matchedStatements()).isEmpty();
    }

    @Test
    void fieldDenyOverridesAllowAndDefaultVisibility() {
        CompiledFieldRule allow = new CompiledFieldRule(Effect.ALLOW, List.of("description"), TRUE);
        CompiledFieldRule deny = new CompiledFieldRule(Effect.DENY, List.of("description"), TRUE);
        EffectiveAuthorization authorization = authorization(
            statement("allow-field", Target.OBJECT, Effect.ALLOW, false, TRUE, List.of(allow)),
            statement("deny-field", Target.OBJECT, Effect.ALLOW, false, TRUE, List.of(deny))
        );

        AuthorizationEngine.ObjectPlan plan = this.engine.planObjects(
            authorization,
            "GET",
            "/api/v0/projects",
            Map.of()
        );
        AuthorizationEngine.FieldDecision decision = this.engine.authorizeFields(
            plan,
            Map.of(),
            Set.of("id", "description")
        );

        assertThat(decision.hiddenFields()).containsExactly("description");
        assertThat(decision.deniedBy()).containsEntry("description", List.of("deny-field"));
        assertThat(decision.hiddenFields()).doesNotContain("id");
    }

    private static EffectiveAuthorization authorization(CompiledStatement... statements) {
        return new EffectiveAuthorization(
            java.util.Arrays
                .stream(statements)
                .map(statement -> new EffectiveStatement(
                    statement,
                    List.of(new Provenance(List.of("user:test", "statement:" + statement.key())))
                ))
                .toList()
        );
    }

    private static CompiledStatement statement(
        String key,
        Target target,
        Effect effect,
        boolean hasTopLevelEffect,
        AuthorizationExpression condition,
        List<CompiledFieldRule> fields
    ) {
        return new CompiledStatement(
            key,
            "GET",
            SafePathPattern.compile("/api/v0/projects"),
            target,
            effect,
            hasTopLevelEffect,
            condition,
            fields,
            Origin.CUSTOM
        );
    }
}

package io.taskmigo.authorization.object.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicates;
import io.taskmigo.authorization.object.domain.ObjectAuthorizationPredicateComposer.Rule;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.authorization.statement.Effect;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationPredicateComposerTest {

    private final ObjectAuthorizationPredicateComposer composer = new ObjectAuthorizationPredicateComposer(
        ObjectAuthorizationPredicates.standard()
    );

    /**
     * Verifies Object Authorization defaults to a predicate that exposes no objects.
     *
     * Given: no target-matching Object Authorization rules.
     * Expect: composition returns a constant-false predicate.
     */
    @Test
    @DisplayName("defaults to deny when no object authorization rule exists")
    void shouldDenyWhenNoObjectAuthorizationRuleExists() {
        // Arrange
        ObjectAuthorizationPredicate<Object> zero = constant(false);

        // Act
        ObjectAuthorizationPredicate<Object> result = this.composer.compose(zero, List.of());

        // Assert
        assertThat(result.isAlwaysFalse()).isTrue();
    }

    /**
     * Verifies a matching allow predicate grants visibility when no deny predicate matches.
     *
     * Given: one constant-true ALLOW predicate and no DENY predicates.
     * Expect: composition simplifies to constant true.
     */
    @Test
    @DisplayName("allows when an object allow predicate is true")
    void shouldAllowWhenObjectAllowPredicateIsTrue() {
        // Arrange
        ObjectAuthorizationPredicate<Object> zero = constant(false);
        List<Rule<Object>> rules = List.of(new Rule<>(Effect.ALLOW, constant(true)));

        // Act
        ObjectAuthorizationPredicate<Object> result = this.composer.compose(zero, rules);

        // Assert
        assertThat(result.isAlwaysTrue()).isTrue();
    }

    /**
     * Verifies Object Authorization preserves deny-overrides composition.
     *
     * Given: constant-true ALLOW and DENY predicates for the same operation.
     * Expect: composition simplifies to constant false.
     */
    @Test
    @DisplayName("denies when an object deny predicate is true alongside an allow")
    void shouldDenyWhenObjectDenyPredicateIsTrueAlongsideAllow() {
        // Arrange
        ObjectAuthorizationPredicate<Object> zero = constant(false);
        List<Rule<Object>> rules = List.of(
            new Rule<>(Effect.ALLOW, constant(true)),
            new Rule<>(Effect.DENY, constant(true))
        );

        // Act
        ObjectAuthorizationPredicate<Object> result = this.composer.compose(zero, rules);

        // Assert
        assertThat(result.isAlwaysFalse()).isTrue();
    }

    /**
     * Verifies residual Object Authorization predicates remain opaque and are not collapsed during composition.
     *
     * Given: one non-constant ALLOW predicate and no matching DENY predicate.
     * Expect: the final predicate remains non-constant for later database translation.
     */
    @Test
    @DisplayName("preserves a residual predicate when only an object allow remains")
    void shouldPreserveResidualPredicateWhenOnlyObjectAllowRemains() {
        // Arrange
        ObjectAuthorizationPredicate<Object> zero = constant(false);
        ObjectAuthorizationPredicate<Object> residual = ObjectAuthorizationPredicateModels.wrap(
            "test-schema",
            new ObjectAuthorizationExpression.Reference("object", List.of("name"))
        );

        // Act
        ObjectAuthorizationPredicate<Object> result = this.composer.compose(
            zero,
            List.of(new Rule<>(Effect.ALLOW, residual))
        );

        // Assert
        assertThat(result.isAlwaysTrue()).isFalse();
        assertThat(result.isAlwaysFalse()).isFalse();
    }

    private static <Q> ObjectAuthorizationPredicate<Q> constant(boolean value) {
        return ObjectAuthorizationPredicateModels.wrap("test-schema", new ObjectAuthorizationExpression.Literal(value));
    }
}

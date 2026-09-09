package io.taskmigo.auth.authorization.object;

import io.taskmigo.embeddedlanguage.LanguageDiagnostic.SourceSpan;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Applies Boolean identities while composing Object Authorization predicates.
@SuppressWarnings({ "checkstyle:NeedBraces", "unchecked" })
final class DefaultObjectAuthorizationPredicates implements ObjectAuthorizationPredicates {
    static final DefaultObjectAuthorizationPredicates INSTANCE = new DefaultObjectAuthorizationPredicates();

    private DefaultObjectAuthorizationPredicates() {}

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> alwaysTrue() {
        return ObjectAuthorizationPredicateFactory.from(
            new UnboundObjectAuthorizationSchema<>(),
            new SemanticAst.Literal(true, LanguageType.Scalar.BOOL, Set.of(), span())
        );
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> alwaysFalse() {
        return ObjectAuthorizationPredicateFactory.from(
            new UnboundObjectAuthorizationSchema<>(),
            new SemanticAst.Literal(false, LanguageType.Scalar.BOOL, Set.of(), span())
        );
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> and(
        ObjectAuthorizationPredicate<Q> left,
        ObjectAuthorizationPredicate<Q> right
    ) {
        requireCompatible(left, right);
        if (left.isAlwaysFalse()) return left;
        if (right.isAlwaysFalse()) return right;
        if (left.isAlwaysTrue()) return right;
        if (right.isAlwaysTrue()) return left;
        return wrap(left, SemanticAst.BinaryOperator.AND, right);
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> or(
        ObjectAuthorizationPredicate<Q> left,
        ObjectAuthorizationPredicate<Q> right
    ) {
        requireCompatible(left, right);
        if (left.isAlwaysTrue()) return left;
        if (right.isAlwaysTrue()) return right;
        if (left.isAlwaysFalse()) return right;
        if (right.isAlwaysFalse()) return left;
        return wrap(left, SemanticAst.BinaryOperator.OR, right);
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> not(ObjectAuthorizationPredicate<Q> predicate) {
        if (predicate.isAlwaysTrue()) return ObjectAuthorizationPredicateFactory.constantLike(predicate, false);
        if (predicate.isAlwaysFalse()) return ObjectAuthorizationPredicateFactory.constantLike(predicate, true);
        SemanticAst.Expression expression = ObjectAuthorizationPredicateFactory.expression(predicate);
        return ObjectAuthorizationPredicateFactory.from(
            new IdentityObjectAuthorizationSchema<>(ObjectAuthorizationPredicateFactory.schemaIdentity(predicate)),
            new SemanticAst.Unary(
                SemanticAst.UnaryOperator.NOT, expression, LanguageType.Scalar.BOOL, expression.dependencies(), span()
            )
        );
    }

    private static <Q> ObjectAuthorizationPredicate<Q> wrap(
        ObjectAuthorizationPredicate<Q> left,
        SemanticAst.BinaryOperator operator,
        ObjectAuthorizationPredicate<Q> right
    ) {
        SemanticAst.Expression l = ObjectAuthorizationPredicateFactory.expression(left);
        SemanticAst.Expression r = ObjectAuthorizationPredicateFactory.expression(right);
        return ObjectAuthorizationPredicateFactory.from(
            new IdentityObjectAuthorizationSchema<>(ObjectAuthorizationPredicateFactory.schemaIdentity(left)),
            new SemanticAst.Binary(operator, l, r, LanguageType.Scalar.BOOL,
                java.util.stream.Stream.of(l, r).flatMap(value -> value.dependencies().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet()), span())
        );
    }

    private static void requireCompatible(ObjectAuthorizationPredicate<?> left, ObjectAuthorizationPredicate<?> right) {
        String leftIdentity = ObjectAuthorizationPredicateFactory.schemaIdentity(left);
        String rightIdentity = ObjectAuthorizationPredicateFactory.schemaIdentity(right);
        if (!leftIdentity.isEmpty() && !rightIdentity.isEmpty() && !leftIdentity.equals(rightIdentity)) {
            throw new IllegalArgumentException("Object Authorization Predicates belong to incompatible schemas");
        }
    }

    private static SourceSpan span() {
        return new SourceSpan(1, 0, 1, 0);
    }

    private record IdentityObjectAuthorizationSchema<Q>(String identity) implements ObjectAuthorizationSchema<Q> {
        @Override public Class<Q> objectType() { return (Class<Q>) Object.class; }
        @Override public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) { return Optional.empty(); }
        @Override public Collection<ObjectAuthorizationField> fields() { return List.of(); }
    }

    private static final class UnboundObjectAuthorizationSchema<Q> implements ObjectAuthorizationSchema<Q> {
        @Override public Class<Q> objectType() { return (Class<Q>) Object.class; }
        @Override public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) { return Optional.empty(); }
        @Override public Collection<ObjectAuthorizationField> fields() { return List.of(); }
        @Override public String identity() { return ""; }
    }
}

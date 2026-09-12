package io.taskmigo.authorization.object;

import io.taskmigo.language.LanguageDiagnostic.SourceSpan;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.SemanticAst;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Creates opaque Object Authorization predicates for trusted resource binders.
public final class ObjectAuthorizationPredicateFactory {

    private ObjectAuthorizationPredicateFactory() {}

    /// Creates an opaque predicate from a trusted Boolean Language expression.
    ///
    /// @param schema the logical object contract the expression is bound to
    /// @param expression the validated Boolean expression
    /// @return an opaque predicate for the supplied schema
    public static <Q> ObjectAuthorizationPredicate<Q> from(
        ObjectAuthorizationSchema<Q> schema,
        SemanticAst.Expression expression
    ) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(expression);
        if (expression.type() != LanguageType.Scalar.BOOL) {
            throw new IllegalArgumentException("object predicate must be Bool");
        }
        return new LogicalObjectAuthorizationPredicate<>(schema.identity(), expression);
    }

    /// Creates an opaque constant predicate for a logical object contract.
    ///
    /// @param schema the logical object contract
    /// @param value the constant authorization result
    /// @return an opaque constant predicate
    public static <Q> ObjectAuthorizationPredicate<Q> constant(ObjectAuthorizationSchema<Q> schema, boolean value) {
        return from(schema, new SemanticAst.Literal(value, LanguageType.Scalar.BOOL, Set.of(), span()));
    }

    /// Creates a constant predicate that preserves another predicate's schema identity.
    ///
    /// @param predicate the predicate whose schema identity is retained
    /// @param value the constant authorization result
    /// @return an opaque constant predicate
    public static <Q> ObjectAuthorizationPredicate<Q> constantLike(
        ObjectAuthorizationPredicate<?> predicate,
        boolean value
    ) {
        return from(
            new IdentityObjectAuthorizationSchema<>(schemaIdentity(predicate)),
            new SemanticAst.Literal(value, LanguageType.Scalar.BOOL, Set.of(), span())
        );
    }

    /// Extracts the trusted Language expression for a persistence binder.
    ///
    /// @param predicate the predicate produced by this factory
    /// @return the predicate's Language expression
    public static SemanticAst.Expression expression(ObjectAuthorizationPredicate<?> predicate) {
        if (!(predicate instanceof LogicalObjectAuthorizationPredicate<?> logical)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        return logical.expression();
    }

    /// Returns the stable logical schema identity carried by a predicate.
    ///
    /// @param predicate the predicate produced by this factory
    /// @return the predicate's schema identity
    public static String schemaIdentity(ObjectAuthorizationPredicate<?> predicate) {
        if (!(predicate instanceof LogicalObjectAuthorizationPredicate<?> logical)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        return logical.schemaIdentity();
    }

    private static SourceSpan span() {
        return new SourceSpan(1, 0, 1, 0);
    }

    private record LogicalObjectAuthorizationPredicate<Q>(
        String schemaIdentity,
        SemanticAst.Expression expression
    ) implements ObjectAuthorizationPredicate<Q> {
        @Override
        public boolean isAlwaysTrue() {
            return this.expression instanceof SemanticAst.Literal literal && Boolean.TRUE.equals(literal.value());
        }

        @Override
        public boolean isAlwaysFalse() {
            return this.expression instanceof SemanticAst.Literal literal && Boolean.FALSE.equals(literal.value());
        }
    }

    private record IdentityObjectAuthorizationSchema<Q>(String identity) implements ObjectAuthorizationSchema<Q> {
        @SuppressWarnings("unchecked")
        @Override
        public Class<Q> objectType() {
            return (Class<Q>) Object.class;
        }

        @Override
        public Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path) {
            return Optional.empty();
        }

        @Override
        public Collection<ObjectAuthorizationField> fields() {
            return List.of();
        }
    }
}

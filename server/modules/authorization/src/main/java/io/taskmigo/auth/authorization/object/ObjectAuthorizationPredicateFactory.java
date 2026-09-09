package io.taskmigo.auth.authorization.object;

import io.taskmigo.embeddedlanguage.LanguageDiagnostic.SourceSpan;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// Creates opaque Object Authorization predicates for trusted resource binders.
final class ObjectAuthorizationPredicateFactory {

    private ObjectAuthorizationPredicateFactory() {}

    static <Q> ObjectAuthorizationPredicate<Q> from(
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

    static <Q> ObjectAuthorizationPredicate<Q> constant(ObjectAuthorizationSchema<Q> schema, boolean value) {
        return from(schema, new SemanticAst.Literal(value, LanguageType.Scalar.BOOL, Set.of(), span()));
    }

    static <Q> ObjectAuthorizationPredicate<Q> constantLike(ObjectAuthorizationPredicate<?> predicate, boolean value) {
        return from(
            new IdentityObjectAuthorizationSchema<>(schemaIdentity(predicate)),
            new SemanticAst.Literal(value, LanguageType.Scalar.BOOL, Set.of(), span())
        );
    }

    static SemanticAst.Expression expression(ObjectAuthorizationPredicate<?> predicate) {
        if (!(predicate instanceof LogicalObjectAuthorizationPredicate<?> logical)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        return logical.expression();
    }

    static String schemaIdentity(ObjectAuthorizationPredicate<?> predicate) {
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

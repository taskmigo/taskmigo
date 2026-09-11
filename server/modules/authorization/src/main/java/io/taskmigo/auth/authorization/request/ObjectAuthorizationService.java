package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.auth.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.auth.authorization.object.ObjectAuthorization;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationPredicateFactory;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationPredicates;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchemaValidator;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageException;
import io.taskmigo.embeddedlanguage.EmbeddedLanguagePartialEvaluator;
import io.taskmigo.embeddedlanguage.LanguageDiagnostic;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.PartialProgram;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/// Evaluates effective object Statements into opaque Object Authorization predicates.
@Service
public class ObjectAuthorizationService implements ObjectAuthorization {

    private final EmbeddedLanguagePartialEvaluator partialEvaluator;
    private final EmbeddedLanguageCompiler compiler;
    private final ObjectAuthorizationSchemaRegistry schemaRegistry;

    /// Creates the service with the compiler and application-owned object route registry.
    public ObjectAuthorizationService(
        EmbeddedLanguagePartialEvaluator partialEvaluator,
        EmbeddedLanguageCompiler compiler,
        ObjectAuthorizationSchemaRegistry schemaRegistry
    ) {
        this.partialEvaluator = partialEvaluator;
        this.compiler = compiler;
        this.schemaRegistry = schemaRegistry;
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        ObjectAuthorizationSchema<Q> schema
    ) {
        if (!(context instanceof AuthorizationOperation operation)) {
            throw new AuthorizationException("authorization context is not valid for this operation");
        }
        try {
            ObjectAuthorizationPredicate<Q> allows = ObjectAuthorizationPredicateFactory.constant(schema, false);
            ObjectAuthorizationPredicate<Q> denies = ObjectAuthorizationPredicateFactory.constant(schema, false);
            for (var artifact : operation.snapshot().executableStatements()) {
                var statement = artifact.statement();
                if (statement.scope() != Scope.OBJECT || !artifact.matches(operation.method(), operation.path())) {
                    continue;
                }
                SemanticAst policy = operation.snapshot().compiledPolicy(statement);
                ObjectAuthorizationSchemaValidator.validate(policy.expression(), schema);
                ObjectAuthorizationPredicate<Q> predicate = ObjectAuthorizationPredicateFactory.from(
                    schema,
                    partial(policy, operation.snapshot().roots())
                );
                if (statement.effect() == Effect.ALLOW) {
                    allows = ObjectAuthorizationPredicates.standard().or(allows, predicate);
                } else {
                    denies = ObjectAuthorizationPredicates.standard().or(denies, predicate);
                }
            }
            return ObjectAuthorizationPredicates.standard().and(
                allows,
                ObjectAuthorizationPredicates.standard().not(denies)
            );
        } catch (EmbeddedLanguageException | IllegalArgumentException exception) {
            throw new AuthorizationException("Invalid Object authorization policy: " + exception.getMessage());
        }
    }

    /// Validates an object policy independently against every schema governed by its target.
    @Override
    public void validatePolicy(String policy, String method, String path) {
        try {
            List<ObjectAuthorizationSchema<?>> applicable = this.schemaRegistry.applicable(method, path);
            if (applicable.isEmpty()) {
                throw new AuthorizationException("Object Statement target matches no registered object schema route");
            }
            for (ObjectAuthorizationSchema<?> schema : applicable) {
                SemanticAst compiled = this.compiler.compile(
                    policy,
                    AuthorizationEmbeddedLanguageSchemas.object(schema),
                    AuthorizationCompilationProfile.policy()
                );
                ObjectAuthorizationSchemaValidator.validate(compiled.expression(), schema);
            }
        } catch (EmbeddedLanguageException exception) {
            throw exception;
        }
    }

    private SemanticAst.Expression partial(SemanticAst policy, Map<String, ?> roots) {
        PartialProgram result = this.partialEvaluator.partial(policy, roots);
        if (result instanceof PartialProgram.Concrete concrete) {
            if (!(concrete.value() instanceof Boolean value)) {
                throw new AuthorizationException("Object authorization policy result is not Bool");
            }
            return new SemanticAst.Literal(
                value,
                LanguageType.Scalar.BOOL,
                new LanguageDiagnostic.SourceSpan(1, 0, 1, 0)
            );
        }
        SemanticAst.Expression residual = ((PartialProgram.Residual) result).expression();
        if (residual.type() != LanguageType.Scalar.BOOL) {
            throw new AuthorizationException("Object authorization policy result is not Bool");
        }
        return residual;
    }
}

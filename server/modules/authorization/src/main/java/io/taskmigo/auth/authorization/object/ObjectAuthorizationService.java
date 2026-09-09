package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.auth.authorization.request.AuthorizationContext;
import io.taskmigo.auth.authorization.request.AuthorizationOperation;
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
    private final List<ObjectAuthorizationSchema<?>> schemas;

    /// Creates the service with the compiler and registered logical object contracts.
    public ObjectAuthorizationService(
        EmbeddedLanguagePartialEvaluator partialEvaluator,
        EmbeddedLanguageCompiler compiler,
        List<ObjectAuthorizationSchema<?>> schemas
    ) {
        this.partialEvaluator = partialEvaluator;
        this.compiler = compiler;
        this.schemas = List.copyOf(schemas);
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

    /// Validates an object policy against the registered logical object contract.
    public void validatePolicy(String policy, String method, String path) {
        try {
            SemanticAst compiled = this.compiler.compile(
                policy,
                AuthorizationEmbeddedLanguageSchemas.object(this.schemas)
            );
            if (compiled.resultType() != LanguageType.Scalar.BOOL) {
                throw new AuthorizationException("Object authorization policy must return Bool");
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

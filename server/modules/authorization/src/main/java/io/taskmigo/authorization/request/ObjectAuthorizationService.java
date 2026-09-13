package io.taskmigo.authorization.request;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicates;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.authorization.object.persistence.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.persistence.ObjectAuthorizationExpressionValidator;
import io.taskmigo.authorization.object.persistence.ObjectAuthorizationPredicateModels;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.PartialProgram;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/// Evaluates effective object Statements into opaque Object Authorization predicates.
@Service
public class ObjectAuthorizationService implements ObjectAuthorization {

    private final LanguageCompiler compiler;
    private final ObjectAuthorizationSchemaRegistry schemaRegistry;

    /// Creates the service with the compiler and application-owned object route registry.
    public ObjectAuthorizationService(LanguageCompiler compiler, ObjectAuthorizationSchemaRegistry schemaRegistry) {
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
            ObjectAuthorizationPredicate<Q> allows = ObjectAuthorizationPredicateModels.constant(schema, false);
            ObjectAuthorizationPredicate<Q> denies = ObjectAuthorizationPredicateModels.constant(schema, false);
            for (var artifact : operation.snapshot().executableStatements()) {
                var statement = artifact.statement();
                if (statement.scope() == Scope.OBJECT && artifact.matches(operation.method(), operation.path())) {
                    CompiledSource policy = artifact.policy();
                    ObjectAuthorizationExpressionValidator.validate(
                        policy.map(LanguageObjectAuthorizationExpressionVisitor.INSTANCE),
                        schema
                    );
                    ObjectAuthorizationPredicate<Q> predicate = ObjectAuthorizationPredicateModels.from(
                        schema,
                        this.partial(policy, operation.snapshot().roots())
                    );
                    if (statement.effect() == Effect.ALLOW) {
                        allows = ObjectAuthorizationPredicates.standard().or(allows, predicate);
                    } else {
                        denies = ObjectAuthorizationPredicates.standard().or(denies, predicate);
                    }
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
        List<ObjectAuthorizationSchema<?>> applicable = this.schemaRegistry.applicable(method, path);
        if (applicable.isEmpty()) {
            throw new AuthorizationException("Object Statement target matches no registered object schema route");
        }
        for (ObjectAuthorizationSchema<?> schema : applicable) {
            CompiledSource compiled = this.compiler.compile(
                policy,
                AuthorizationEmbeddedLanguageSchemas.object(schema),
                AuthorizationCompilationProfile.policy()
            );
            ObjectAuthorizationExpressionValidator.validate(
                compiled.map(LanguageObjectAuthorizationExpressionVisitor.INSTANCE),
                schema
            );
        }
    }

    private ObjectAuthorizationExpression partial(CompiledSource policy, Map<String, ?> roots) {
        PartialProgram result = policy.partialEvaluate(roots);
        if (result instanceof PartialProgram.Concrete concrete) {
            if (!(concrete.value() instanceof Boolean value)) {
                throw new AuthorizationException("Object authorization policy result is not Bool");
            }
            return new ObjectAuthorizationExpression.Literal(value);
        }
        if (result.type() != LanguageType.Scalar.BOOL) {
            throw new AuthorizationException("Object authorization policy result is not Bool");
        }
        return ((PartialProgram.Residual) result).map(LanguageObjectAuthorizationExpressionVisitor.INSTANCE);
    }
}

package io.taskmigo.authorization.object.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicates;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.object.domain.ObjectAuthorizationPredicateComposer;
import io.taskmigo.authorization.object.domain.ObjectAuthorizationPredicateComposer.Rule;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.authorization.request.application.model.AuthorizationOperation;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.PartialProgram;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Orchestrates Object Authorization Language work and delegates final predicate composition to the pure domain composer.
public final class ObjectAuthorizationService implements ObjectAuthorization {

    private static final ObjectAuthorizationPredicateComposer COMPOSER = new ObjectAuthorizationPredicateComposer(
        ObjectAuthorizationPredicates.standard()
    );

    private final LanguageCompiler compiler;
    private final ObjectAuthorizationTargetResolver targetResolver;

    /// Creates the service with the compiler and application-owned Object Authorization target resolver.
    public ObjectAuthorizationService(LanguageCompiler compiler, ObjectAuthorizationTargetResolver targetResolver) {
        this.compiler = compiler;
        this.targetResolver = targetResolver;
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
            List<Rule<Q>> rules = new ArrayList<>();
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
                    rules.add(new Rule<>(statement.effect(), predicate));
                }
            }
            return COMPOSER.compose(ObjectAuthorizationPredicateModels.constant(schema, false), rules);
        } catch (EmbeddedLanguageException | IllegalArgumentException exception) {
            throw new AuthorizationException("Invalid Object authorization policy: " + exception.getMessage());
        }
    }

    /// Validates an object policy independently against every schema governed by its target.
    @Override
    public void validatePolicy(String policy, String method, String path) {
        List<ObjectAuthorizationSchema<?>> applicable = this.targetResolver.applicable(method, path);
        if (applicable.isEmpty()) {
            throw new AuthorizationException("Object Statement target matches no registered object schema route");
        }
        for (ObjectAuthorizationSchema<?> schema : applicable) {
            CompiledSource compiled = this.compiler.compile(
                policy,
                AuthorizationEmbeddedLanguageSchemas.object(schema),
                AuthorizationCompilationProfile.objectPolicy()
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

package io.taskmigo.authorization.request.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationCompilationProfile;
import io.taskmigo.authorization.embeddedlanguage.AuthorizationEmbeddedLanguageSchemas;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.language.CompilationProfile;
import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.EmbeddedLanguageException;
import io.taskmigo.language.EnvironmentSchema;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QuerySchemaView;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/// Builds fresh executable Statement derivatives from authoritative Statement state.
public final class StatementArtifactFactory {

    private final LanguageCompiler compiler;
    private final ObjectAuthorizationTargetResolver targetResolver;

    public StatementArtifactFactory(LanguageCompiler compiler, ObjectAuthorizationTargetResolver targetResolver) {
        this.compiler = compiler;
        this.targetResolver = targetResolver;
    }

    public List<StatementExecutionArtifact> build(
        Collection<EffectiveStatement> statements,
        String requestMethod,
        String requestPath
    ) {
        List<StatementExecutionArtifact> result = new ArrayList<>();
        for (EffectiveStatement effective : statements) {
            StatementInfo statement = effective.statement();
            if (!methodMatches(statement, requestMethod)) {
                continue;
            }
            StatementTargetPathMatcher pathMatcher = StatementTargetPathMatcher.compile(
                statement.target().api().path()
            );
            if (!pathMatcher.matches(requestPath)) {
                continue;
            }
            EnvironmentSchema schema = this.schema(statement, pathMatcher);
            CompilationProfile profile = profile(statement);
            CompiledSource policy = this.compile(statement, schema, profile);
            result.add(new StatementExecutionArtifact(statement, policy, pathMatcher));
        }
        return List.copyOf(result);
    }

    private EnvironmentSchema schema(StatementInfo statement, StatementTargetPathMatcher pathMatcher) {
        if (statement.scope() == Scope.REQUEST) {
            return AuthorizationEmbeddedLanguageSchemas.request();
        }
        List<QuerySchemaView> applicable = this.targetResolver.applicable(
            statement.target().api().method(),
            pathMatcher
        );
        return AuthorizationEmbeddedLanguageSchemas.object(applicable);
    }

    private static CompilationProfile profile(StatementInfo statement) {
        return switch (statement.scope()) {
            case REQUEST -> AuthorizationCompilationProfile.requestPolicy();
            case OBJECT -> AuthorizationCompilationProfile.objectPolicy();
        };
    }

    private CompiledSource compile(StatementInfo statement, EnvironmentSchema schema, CompilationProfile profile) {
        try {
            return this.compiler.compile(statement.policy(), schema, profile);
        } catch (EmbeddedLanguageException exception) {
            throw new AuthorizationException("Invalid Statement policy: " + exception.getMessage());
        }
    }

    private static boolean methodMatches(StatementInfo statement, String requestMethod) {
        return statement.target().api().method().equals("*") || statement.target().api().method().equals(requestMethod);
    }
}

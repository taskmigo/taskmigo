package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.resolution.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.authorization.request.application.model.AuthorizationOperation;
import io.taskmigo.authorization.request.application.model.AuthorizationSnapshot;
import io.taskmigo.authorization.request.application.resolution.EffectiveStatement;
import io.taskmigo.authorization.request.application.service.StatementArtifactFactory;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.query.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationRuntimeContextTest {

    @Test
    @DisplayName("authorization predicate identity uses the exact runtime Query field context")
    void shouldUseRuntimeQueryContextForAuthorizationIdentity() {
        QuerySchemaView schema = contextAwareSchema();
        ObjectAuthorizationTargetResolver targets = ObjectAuthorizationTargetResolver.all(List.of(schema));
        StatementArtifactFactory artifacts = new StatementArtifactFactory(new LanguageCompiler(), targets);
        ObjectAuthorizationService service = new ObjectAuthorizationService(new LanguageCompiler(), targets);
        StatementInfo statement = statement("object.severity == \"HIGH\"");
        AuthorizationOperation operation = operation(statement, artifacts);
        QueryFieldContext context = new QueryFieldContext(Map.of("scope", "incident"));

        ObjectAuthorizationPredicate<TestObject> predicate = service.authorize(operation, schema, context);

        assertThat(ObjectAuthorizationPredicateModels.schemaIdentity(predicate)).isEqualTo(schema.identity(context));
        assertThat(ObjectAuthorizationPredicateModels.schemaIdentity(predicate)).isNotEqualTo(schema.identity());
    }

    @Test
    @DisplayName("runtime validation fails closed when the requested context does not expose the object field")
    void shouldFailClosedWhenRuntimeContextHidesField() {
        QuerySchemaView schema = contextAwareSchema();
        ObjectAuthorizationTargetResolver targets = ObjectAuthorizationTargetResolver.all(List.of(schema));
        StatementArtifactFactory artifacts = new StatementArtifactFactory(new LanguageCompiler(), targets);
        ObjectAuthorizationService service = new ObjectAuthorizationService(new LanguageCompiler(), targets);
        AuthorizationOperation operation = operation(statement("object.severity == \"HIGH\""), artifacts);
        QueryFieldContext hidden = new QueryFieldContext(Map.of("scope", "hidden"));

        assertThatThrownBy(() -> service.authorize(operation, schema, hidden))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("object path is not queryable");
    }

    private static QuerySchemaView contextAwareSchema() {
        QueryFieldDescriptor global = new QueryFieldDescriptor(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            Set.of(QueryOperator.EQ)
        );
        QueryFieldDescriptor scoped = new QueryFieldDescriptor(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            false,
            Set.of(QueryOperator.EQ)
        );
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return "tickets.list";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                Object scope = context.values().get("scope");
                if (scope == null) {
                    return List.of(global);
                }
                return "incident".equals(scope) ? List.of(scoped) : List.of();
            }
        };
    }

    private static StatementInfo statement(String policy) {
        return new StatementInfo(
            UUID.randomUUID(),
            "ticket_object_statement",
            null,
            Effect.ALLOW,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/tickets")),
            policy
        );
    }

    private static AuthorizationOperation operation(StatementInfo statement, StatementArtifactFactory artifacts) {
        return new AuthorizationOperation(
            new AuthorizationSnapshot(
                UUID.randomUUID(),
                artifacts.build(List.of(new EffectiveStatement(statement)), "GET", "/api/v0/tickets"),
                Map.of()
            ),
            "GET",
            "/api/v0/tickets"
        );
    }

    private static final class TestObject {}
}

package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.model.AuthorizationOperation;
import io.taskmigo.authorization.request.application.model.AuthorizationSnapshot;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.request.application.service.StatementArtifactFactory;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationServiceTest {

    private final QuerySchemaView schema = schema("name");
    private final ObjectAuthorizationTargetResolver targets = ObjectAuthorizationTargetResolver.all(
        List.of(this.schema)
    );
    private final StatementArtifactFactory artifacts = new StatementArtifactFactory(
        new LanguageCompiler(),
        this.targets
    );
    private final ObjectAuthorizationService service = new ObjectAuthorizationService(
        new LanguageCompiler(),
        this.targets
    );

    @Test
    @DisplayName("defaults to deny without a matching object statement")
    void shouldDefaultDenyWithoutMatchingObjectStatement() {
        AuthorizationOperation operation = new AuthorizationOperation(
            new AuthorizationSnapshot(UUID.randomUUID(), List.of(), Map.of()),
            "GET",
            "/api/v0/objects"
        );
        ObjectAuthorizationPredicate<TestObject> predicate = this.service.authorize(operation, this.schema);
        assertThat(predicate.isAlwaysFalse()).isTrue();
    }

    @Test
    @DisplayName("keeps object-dependent policies opaque")
    void shouldKeepObjectDependentPolicyOpaque() {
        ObjectAuthorizationPredicate<TestObject> predicate = this.service.authorize(
            operation(statement(Effect.ALLOW, "object.name == \"alice\"")),
            this.schema
        );
        assertThat(predicate.isAlwaysTrue()).isFalse();
        assertThat(predicate.isAlwaysFalse()).isFalse();
    }

    @Test
    @DisplayName("validates contains against the shared query schema")
    void shouldValidateContainsAgainstSharedQuerySchema() {
        QuerySchemaView containsSchema = schema("name", Set.of(QueryOperator.EQ, QueryOperator.CONTAINS));
        ObjectAuthorizationService containsService = new ObjectAuthorizationService(
            new LanguageCompiler(),
            ObjectAuthorizationTargetResolver.all(List.of(containsSchema))
        );
        assertThatCode(() ->
            containsService.validatePolicy("contains(object.name, \"ali\")", "GET", "/api/v0/objects")
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects unknown object paths")
    void shouldRejectUnknownObjectPath() {
        assertThatThrownBy(() ->
            this.service.validatePolicy("object.other == \"alice\"", "GET", "/api/v0/objects")
        ).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("fails closed for concrete non-boolean results")
    void shouldFailClosedForConcreteNonBooleanResult() {
        assertThatThrownBy(() -> this.service.authorize(operation(statement(Effect.ALLOW, "42")), this.schema))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("not Bool");
    }

    private AuthorizationOperation operation(StatementInfo statement) {
        return new AuthorizationOperation(
            new AuthorizationSnapshot(
                UUID.randomUUID(),
                this.artifacts.build(List.of(new EffectiveStatement(statement)), "GET", "/api/v0/objects"),
                Map.of()
            ),
            "GET",
            "/api/v0/objects"
        );
    }

    private static StatementInfo statement(Effect effect, String policy) {
        return new StatementInfo(
            UUID.randomUUID(),
            "object_statement",
            null,
            effect,
            Scope.OBJECT,
            new TargetInfo(new ApiInfo("GET", "/api/v0/objects")),
            policy
        );
    }

    private static QuerySchemaView schema(String path) {
        return schema(
            path,
            Set.of(
                QueryOperator.EQ,
                QueryOperator.NE,
                QueryOperator.GT,
                QueryOperator.GTE,
                QueryOperator.LT,
                QueryOperator.LTE
            )
        );
    }

    private static QuerySchemaView schema(String path, Set<QueryOperator> operators) {
        QueryFieldDescriptor field = new QueryFieldDescriptor(
            QueryPath.parse(path),
            TypeDescriptor.of(String.class),
            false,
            operators
        );
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return TestObject.class.getName();
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of(field);
            }
        };
    }

    private static final class TestObject {}
}

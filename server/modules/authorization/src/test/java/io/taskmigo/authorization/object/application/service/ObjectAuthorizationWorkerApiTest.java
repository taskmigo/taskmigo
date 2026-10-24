package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.resolution.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import io.taskmigo.authorization.request.application.api.RequestAuthorization;
import io.taskmigo.authorization.request.application.model.AuthorizationOperation;
import io.taskmigo.authorization.request.application.model.AuthorizationSnapshot;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationWorkerApiTest {

    @Test
    @DisplayName("authorizes an explicit principal and logical request for non-HTTP callers")
    void shouldAuthorizeExplicitPrincipalAndRequest() {
        AuthorizationPrincipal principal = new AuthorizationPrincipal(UUID.randomUUID(), "worker-user");
        AuthorizationRequest request = new AuthorizationRequest("PATCH", "/api/v0/tickets/{ticketId}", Map.of());
        AuthorizationOperation operation = new AuthorizationOperation(
            new AuthorizationSnapshot(principal.id(), List.of(), Map.of()),
            request.method(),
            request.path()
        );
        RequestAuthorization requestAuthorization = mock(RequestAuthorization.class);
        when(requestAuthorization.authorize(principal, request)).thenReturn(new RequestAuthorizationResult(true, operation));
        TestAuthorizationSchema schema = new TestAuthorizationSchema();
        ObjectAuthorizationService service = new ObjectAuthorizationService(
            new LanguageCompiler(),
            ObjectAuthorizationTargetResolver.all(List.of(schema)),
            requestAuthorization
        );

        ObjectAuthorizationPredicate<TestObject> predicate = service.authorize(
            principal,
            request,
            schema,
            QueryFieldContext.empty()
        );

        assertThat(predicate.isAlwaysFalse()).isTrue();
        verify(requestAuthorization).authorize(principal, request);
    }

    private static final class TestAuthorizationSchema implements ObjectAuthorizationSchema<TestObject> {

        @Override
        public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
            return List.of(
                new QueryFieldDescriptor(
                    QueryPath.parse("name"),
                    TypeDescriptor.of(String.class),
                    false,
                    Set.of(QueryOperator.EQ)
                )
            );
        }
    }

    private static final class TestObject {}
}

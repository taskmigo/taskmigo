package io.taskmigo.authorization.object.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorizationResult;
import io.taskmigo.authorization.request.application.api.RequestAuthorization;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ObjectAuthorizationWorkerApiTest {

    @Test
    @DisplayName("authorizes an explicit principal and logical request for non-HTTP callers")
    void shouldAuthorizeExplicitPrincipalAndRequest() {
        AuthorizationPrincipal principal = new AuthorizationPrincipal(UUID.randomUUID(), "worker-user");
        AuthorizationRequest request = new AuthorizationRequest("PATCH", "/api/v0/tickets/{ticketId}", Map.of());
        AuthorizationContext context = mock(AuthorizationContext.class);
        RequestAuthorization requestAuthorization = mock(RequestAuthorization.class);
        ObjectAuthorization objectAuthorization = mock(ObjectAuthorization.class);
        TestAuthorizationSchema schema = new TestAuthorizationSchema();
        ObjectAuthorizationPredicate<TestObject> predicate = mock(ObjectAuthorizationPredicate.class);
        when(requestAuthorization.authorize(principal, request)).thenReturn(
            new RequestAuthorizationResult(true, context)
        );
        when(objectAuthorization.authorize(context, schema, QueryFieldContext.empty())).thenReturn(predicate);
        DefaultAuthorizationGateway gateway = new DefaultAuthorizationGateway(
            requestAuthorization,
            objectAuthorization
        );

        ObjectAuthorizationPredicate<TestObject> resolved = gateway.authorize(principal, request, schema);

        assertThat(resolved).isSameAs(predicate);
        verify(requestAuthorization).authorize(principal, request);
        verify(objectAuthorization).authorize(context, schema, QueryFieldContext.empty());
    }

    @Test
    @DisplayName("fails closed before Object Authorization when explicit Request Authorization is denied")
    void shouldFailClosedWhenRequestAuthorizationDenied() {
        AuthorizationPrincipal principal = new AuthorizationPrincipal(UUID.randomUUID(), "worker-user");
        AuthorizationRequest request = new AuthorizationRequest("PATCH", "/api/v0/tickets/{ticketId}", Map.of());
        RequestAuthorization requestAuthorization = mock(RequestAuthorization.class);
        ObjectAuthorization objectAuthorization = mock(ObjectAuthorization.class);
        TestAuthorizationSchema schema = new TestAuthorizationSchema();
        when(requestAuthorization.authorize(principal, request)).thenReturn(
            new RequestAuthorizationResult(false, mock(AuthorizationContext.class))
        );
        DefaultAuthorizationGateway gateway = new DefaultAuthorizationGateway(
            requestAuthorization,
            objectAuthorization
        );

        assertThatThrownBy(() -> gateway.authorize(principal, request, schema))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("denied");
        verifyNoInteractions(objectAuthorization);
    }

    private static final class TestAuthorizationSchema implements ObjectAuthorizationSchema<TestObject> {

        @Override
        public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
            return List.of();
        }
    }

    private static final class TestObject {}
}

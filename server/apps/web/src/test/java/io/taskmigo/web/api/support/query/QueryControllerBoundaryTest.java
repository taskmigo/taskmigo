package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperation;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryControllerBoundaryTest {

    private static final List<String> QUERY_CONTROLLERS = List.of(
        "io.taskmigo.web.api.v0.audit.AuditController",
        "io.taskmigo.web.api.v0.auth.authorization.RoleController",
        "io.taskmigo.web.api.v0.auth.authorization.StatementController",
        "io.taskmigo.web.api.v0.auth.group.GroupController",
        "io.taskmigo.web.api.v0.auth.user.UserController",
        "io.taskmigo.web.api.v0.ticket.TicketController"
    );

    /**
     * Verifies that server-derived Query and Object Authorization state stays behind the HTTP controller boundary.
     *
     * Given: every controller handler that declares a Query operation.
     * Expect: no handler exposes QueryFieldContext or ObjectAuthorizationPredicate as method parameters.
     */
    @Test
    @DisplayName("keeps server-owned query and authorization state out of controller parameters")
    void shouldKeepServerOwnedQueryStateOutOfControllerParameters() throws ClassNotFoundException {
        // Arrange / Act / Assert
        for (String controllerName : QUERY_CONTROLLERS) {
            Class<?> controller = Class.forName(controllerName);
            for (Method method : controller.getDeclaredMethods()) {
                if (method.getAnnotation(QueryOperation.class) == null) {
                    continue;
                }
                assertThat(method.getParameterTypes())
                    .as("server-owned parameters on %s#%s", controllerName, method.getName())
                    .doesNotContain(QueryFieldContext.class, ObjectAuthorizationPredicate.class);
            }
        }
    }
}

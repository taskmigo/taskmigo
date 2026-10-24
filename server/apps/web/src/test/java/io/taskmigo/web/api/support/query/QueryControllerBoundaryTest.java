package io.taskmigo.web.api.support.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.object.AuthorizationContract;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.query.QueryFieldContext;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QueryControllerBoundaryTest {

    private static final List<String> CONTROLLERS = List.of(
        "io.taskmigo.web.api.v0.audit.AuditController",
        "io.taskmigo.web.api.v0.auth.authorization.RoleController",
        "io.taskmigo.web.api.v0.auth.authorization.StatementController",
        "io.taskmigo.web.api.v0.auth.group.GroupController",
        "io.taskmigo.web.api.v0.auth.user.UserController",
        "io.taskmigo.web.api.v0.ticket.TicketController"
    );

    @Test
    @DisplayName("keeps runtime field context internal and requires an Authorization Contract for injected predicates")
    void shouldKeepControllerContractBoundaryExplicit() throws ClassNotFoundException {
        for (String controllerName : CONTROLLERS) {
            Class<?> controller = Class.forName(controllerName);
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(method.getParameterTypes())
                    .as("runtime field context on %s#%s", controllerName, method.getName())
                    .doesNotContain(QueryFieldContext.class);
                if (List.of(method.getParameterTypes()).contains(ObjectAuthorizationPredicate.class)) {
                    assertThat(method.getAnnotation(AuthorizationContract.class))
                        .as("authorization contract on %s#%s", controllerName, method.getName())
                        .isNotNull();
                }
            }
        }
    }
}

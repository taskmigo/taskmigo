package io.taskmigo.migration.configuration;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.application.resolution.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.role.application.api.RoleAuthorization;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.authorization.statement.application.api.StatementAuthorization;
import io.taskmigo.identity.group.application.api.GroupAuthorization;
import io.taskmigo.identity.user.application.api.UserAuthorization;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// Supplies migration-time target metadata needed to validate managed Object Statements.
@Configuration(proxyBeanMethods = false)
class AuthorizationObjectSchemaConfiguration {

    @Bean
    @Primary
    ObjectAuthorizationTargetResolver objectAuthorizationTargetResolver(
        UserAuthorization.List userList,
        UserAuthorization.Delete userDelete,
        UserAuthorization.UpdateStatements userUpdateStatements,
        GroupAuthorization.List groupList,
        RoleAuthorization.List roleList,
        StatementAuthorization.List statementList
    ) {
        List<Route> routes = List.of(
            new Route("GET", "/api/v0/users", userList),
            new Route("DELETE", "/api/v0/users/{userId}", userDelete),
            new Route("PATCH", "/api/v0/users/{userId}/statements", userUpdateStatements),
            new Route("GET", "/api/v0/groups", groupList),
            new Route("GET", "/api/v0/roles", roleList),
            new Route("GET", "/api/v0/statements", statementList)
        );
        return (method, pathMatcher) -> {
            ArrayList<ObjectAuthorizationSchema<?>> applicable = new ArrayList<>();
            for (Route route : routes) {
                if (route.matches(method, pathMatcher) && !applicable.contains(route.schema())) {
                    applicable.add(route.schema());
                }
            }
            return List.copyOf(applicable);
        };
    }

    private record Route(String method, String path, ObjectAuthorizationSchema<?> schema) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || this.method.equals(statementMethod)) && pathMatcher.matches(this.path)
            );
        }
    }
}

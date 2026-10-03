package io.taskmigo.migration.infrastructure.config;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.user.UserInfo;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// Supplies migration-time target metadata needed to validate managed Object Statements.
@Configuration(proxyBeanMethods = false)
class AuthorizationObjectSchemaConfiguration {

    /// Supplies installer target metadata for the versioned collection APIs that exist in the web application.
    @Bean
    @Primary
    ObjectAuthorizationTargetResolver objectAuthorizationTargetResolver(
        ObjectAuthorizationBinding<UserInfo> users,
        ObjectAuthorizationBinding<GroupInfo> groups,
        ObjectAuthorizationBinding<RoleInfo> roles,
        ObjectAuthorizationBinding<StatementInfo> statements
    ) {
        List<Route> routes = List.of(
            new Route("GET", "/api/v0/users", users),
            new Route("DELETE", "/api/v0/users/{userId}", users),
            new Route("PATCH", "/api/v0/users/{userId}/statements", users),
            new Route("GET", "/api/v0/groups", groups),
            new Route("GET", "/api/v0/roles", roles),
            new Route("GET", "/api/v0/statements", statements)
        );
        return (method, pathMatcher) ->
            routes
                .stream()
                .filter(route -> route.matches(method, pathMatcher))
                .<ObjectAuthorizationBinding<?>>map(Route::binding)
                .distinct()
                .toList();
    }

    private record Route(String method, String path, ObjectAuthorizationBinding<?> binding) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || this.method.equals(statementMethod)) && pathMatcher.matches(this.path)
            );
        }
    }
}

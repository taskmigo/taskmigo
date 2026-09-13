package io.taskmigo.bootstrap;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.spi.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.user.UserInfo;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// Supplies bootstrap-time target metadata needed to validate managed Object Statements.
@Configuration(proxyBeanMethods = false)
class AuthorizationObjectSchemaConfiguration {

    /// Supplies installer target metadata for the versioned collection APIs that exist in the web application.
    @Bean
    @Primary
    ObjectAuthorizationTargetResolver objectAuthorizationTargetResolver(
        ObjectAuthorizationSchema<UserInfo> users,
        ObjectAuthorizationSchema<GroupInfo> groups,
        ObjectAuthorizationSchema<RoleInfo> roles,
        ObjectAuthorizationSchema<StatementInfo> statements
    ) {
        List<Route> routes = List.of(
            new Route("GET", "/api/v0/users", users),
            new Route("GET", "/api/v0/groups", groups),
            new Route("GET", "/api/v0/roles", roles),
            new Route("GET", "/api/v0/statements", statements)
        );
        return (method, path) -> routes
            .stream()
            .filter(route -> route.matches(method, path))
            .<ObjectAuthorizationSchema<?>>map(Route::schema)
            .distinct()
            .toList();
    }

    private record Route(String method, String path, ObjectAuthorizationSchema<?> schema) {
        private boolean matches(String statementMethod, String statementPath) {
            return (
                ("*".equals(statementMethod) || this.method.equals(statementMethod)) &&
                Pattern.matches(statementPath, this.path)
            );
        }
    }
}

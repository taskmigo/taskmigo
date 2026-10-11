package io.taskmigo.migration.infrastructure.config;

import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.statement.StatementTargetPathMatcher;
import io.taskmigo.query.QuerySchemaView;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// Supplies migration-time target metadata needed to validate managed Object Statements.
@Configuration(proxyBeanMethods = false)
class AuthorizationObjectSchemaConfiguration {

    @Bean
    @Primary
    ObjectAuthorizationTargetResolver objectAuthorizationTargetResolver(List<QuerySchemaView> schemas) {
        List<Route> routes = schemas
            .stream()
            .flatMap(schema -> routes(schema).stream())
            .toList();
        return (method, pathMatcher) ->
            routes
                .stream()
                .filter(route -> route.matches(method, pathMatcher))
                .map(Route::schema)
                .distinct()
                .toList();
    }

    private static List<Route> routes(QuerySchemaView schema) {
        return switch (schema.operation()) {
            case "identity.users.list" -> List.of(new Route("GET", "/api/v0/users", schema));
            case "identity.users.delete" -> List.of(new Route("DELETE", "/api/v0/users/{userId}", schema));
            case "identity.users.update-statements" -> List.of(
                new Route("PATCH", "/api/v0/users/{userId}/statements", schema)
            );
            case "identity.groups.list" -> List.of(new Route("GET", "/api/v0/groups", schema));
            case "access-control.roles.list" -> List.of(new Route("GET", "/api/v0/roles", schema));
            case "access-control.statements.list" -> List.of(new Route("GET", "/api/v0/statements", schema));
            default -> List.of();
        };
    }

    private record Route(String method, String path, QuerySchemaView schema) {
        private boolean matches(String statementMethod, StatementTargetPathMatcher pathMatcher) {
            return (
                ("*".equals(statementMethod) || this.method.equals(statementMethod)) && pathMatcher.matches(this.path)
            );
        }
    }
}

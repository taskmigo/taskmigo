package io.taskmigo.auth;

import java.util.Map;
import org.springframework.stereotype.Component;

/// Registers the queryable fields for the Statement collection API.
@Component
final class StatementObjectAuthorizationDialect implements AuthorizationObjectQueryDialect {

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String path() {
        return "/api/v0/statements";
    }

    @Override
    public Map<String, Class<?>> fields() {
        return Map.of(
            "id",
            java.util.UUID.class,
            "name",
            String.class,
            "description",
            String.class,
            "method",
            String.class,
            "path",
            String.class
        );
    }

    @Override
    public void validate(AuthorizationCompiler.Expression predicate) {}
}

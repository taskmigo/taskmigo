package io.taskmigo.auth;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/// Registers the queryable fields for the Group collection API.
@Component
final class GroupObjectAuthorizationDialect implements AuthorizationObjectQueryDialect {

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String path() {
        return "/api/v0/groups";
    }

    @Override
    public Map<String, Class<?>> fields() {
        return Map.of("id", UUID.class, "name", String.class, "description", String.class);
    }

    @Override
    public void validate(AuthorizationCompiler.Expression predicate) {}
}

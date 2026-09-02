package io.taskmigo.auth.authorization.object;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/// Registers the queryable fields for the Role collection API.
@Component
final class RoleObjectAuthorizationDialect implements AuthorizationObjectQueryDialect {

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String path() {
        return "/api/v0/roles";
    }

    @Override
    public Map<String, Class<?>> fields() {
        return Map.of("id", UUID.class, "name", String.class, "description", String.class);
    }
}

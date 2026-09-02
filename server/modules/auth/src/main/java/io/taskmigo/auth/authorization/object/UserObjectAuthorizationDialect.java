package io.taskmigo.auth.authorization.object;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/// Registers the queryable fields for the User collection API.
@Component
final class UserObjectAuthorizationDialect implements AuthorizationObjectQueryDialect {

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String path() {
        return "/api/v0/users";
    }

    @Override
    public Map<String, Class<?>> fields() {
        return Map.of("id", UUID.class, "username", String.class, "firstName", String.class, "lastName", String.class);
    }
}

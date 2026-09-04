package io.taskmigo.auth.user;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.policy.AuthorizationResourceAdapter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/// Resolves user authorization resources in one repository batch without exposing UserEntity instances.
@Service
public final class UserAuthorizationResourceAdapter implements AuthorizationResourceAdapter {

    private final UserRepository users;

    /// Creates the user resource adapter.
    public UserAuthorizationResourceAdapter(UserRepository users) {
        this.users = users;
    }

    @Override
    public String type() {
        return "user";
    }

    @Override
    public Map<String, Map<String, ?>> resolve(Collection<String> keys) {
        Map<String, UUID> identifiers = new LinkedHashMap<>();
        for (String key : keys) {
            try {
                identifiers.put(key, UUID.fromString(key));
            } catch (IllegalArgumentException exception) {
                throw new AuthorizationException("Authorization user resource key is not a UUID");
            }
        }
        Map<String, Map<String, ?>> result = new LinkedHashMap<>();
        for (UserEntity user : this.users.findAllById(identifiers.values())) {
            result.put(user.id.toString(), Map.of(
                "id", user.id.toString(),
                "username", user.username,
                "firstName", user.firstName,
                "lastName", user.lastName,
                "emails", user.emails.stream().sorted().toList(),
                "displayName", user.displayName(),
                "status", user.status.name()
            ));
        }
        return Map.copyOf(result);
    }
}

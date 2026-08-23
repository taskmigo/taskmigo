package io.taskmigo.web.security;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

@Component
final class ServicePrincipalAuthorization {

    private final JdbcOperations jdbc;

    ServicePrincipalAuthorization(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    Set<String> permissions(String registeredClientId) {
        return new LinkedHashSet<>(
            jdbc.queryForList(
                """
                select permissions.permission_key
                from oauth_service_principal_permissions permissions
                join oauth_service_principal principal
                  on principal.registered_client_id = permissions.registered_client_id
                where permissions.registered_client_id = ? and principal.enabled
                order by permissions.permission_key
                """,
                String.class,
                registeredClientId
            )
        );
    }
}

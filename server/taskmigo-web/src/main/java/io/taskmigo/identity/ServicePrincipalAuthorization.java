package io.taskmigo.identity;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
final class ServicePrincipalAuthorization {

    private final JdbcClient jdbc;

    ServicePrincipalAuthorization(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Set<String> permissions(String registeredClientId) {
        return new LinkedHashSet<>(
            jdbc
                .sql(
                    """
                    select permissions.permission_key
                    from oauth_service_principal_permissions permissions
                    join oauth_service_principal principal
                      on principal.registered_client_id = permissions.registered_client_id
                    where permissions.registered_client_id = :clientId and principal.enabled
                    order by permissions.permission_key
                    """
                )
                .param("clientId", registeredClientId)
                .query(String.class)
                .list()
        );
    }
}

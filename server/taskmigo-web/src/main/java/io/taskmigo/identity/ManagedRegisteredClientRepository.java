package io.taskmigo.identity;

import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

final class ManagedRegisteredClientRepository implements RegisteredClientRepository {

    private final JdbcRegisteredClientRepository clients;
    private final JdbcClient jdbc;

    ManagedRegisteredClientRepository(JdbcRegisteredClientRepository clients, JdbcClient jdbc) {
        this.clients = clients;
        this.jdbc = jdbc;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        clients.save(registeredClient);
    }

    @Override
    public @Nullable RegisteredClient findById(String id) {
        return enabled(clients.findById(id));
    }

    @Override
    public @Nullable RegisteredClient findByClientId(String clientId) {
        return enabled(clients.findByClientId(clientId));
    }

    private @Nullable RegisteredClient enabled(@Nullable RegisteredClient client) {
        if (client == null) return null;
        Optional<Boolean> enabled = jdbc
            .sql("select enabled from oauth_client_management where registered_client_id = :clientId")
            .param("clientId", client.getId())
            .query(Boolean.class)
            .optional();
        return enabled.orElse(true) ? client : null;
    }
}

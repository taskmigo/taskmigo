package io.taskmigo.web.security;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

final class ManagedRegisteredClientRepository implements RegisteredClientRepository {

    private final JdbcRegisteredClientRepository clients;
    private final JdbcOperations jdbc;

    ManagedRegisteredClientRepository(JdbcRegisteredClientRepository clients, JdbcOperations jdbc) {
        this.clients = clients;
        this.jdbc = jdbc;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        clients.save(registeredClient);
    }

    @Override
    public @Nullable RegisteredClient findById(String id) {
        @Nullable
        RegisteredClient client = clients.findById(id);
        return isEnabled(client) ? client : null;
    }

    @Override
    public @Nullable RegisteredClient findByClientId(String clientId) {
        @Nullable
        RegisteredClient client = clients.findByClientId(clientId);
        return isEnabled(client) ? client : null;
    }

    private boolean isEnabled(@Nullable RegisteredClient client) {
        if (client == null) return false;
        List<Boolean> enabled = jdbc.queryForList(
            "select enabled from oauth_client_management where registered_client_id = ?",
            Boolean.class,
            client.getId()
        );
        return enabled.isEmpty() || Boolean.TRUE.equals(enabled.getFirst());
    }
}

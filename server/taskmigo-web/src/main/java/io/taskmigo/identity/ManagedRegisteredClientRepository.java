package io.taskmigo.identity;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

final class ManagedRegisteredClientRepository implements RegisteredClientRepository {

    private final JdbcRegisteredClientRepository clients;

    ManagedRegisteredClientRepository(JdbcRegisteredClientRepository clients) {
        this.clients = clients;
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
        return !InternalClientMetadata.isManaged(client) || InternalClientMetadata.isEnabled(client) ? client : null;
    }
}

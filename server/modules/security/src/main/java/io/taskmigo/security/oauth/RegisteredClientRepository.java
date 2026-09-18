package io.taskmigo.security.oauth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

/// Defines a registered-client persistence contract with Taskmigo-owned classification.
public interface RegisteredClientRepository
    extends org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
{
    /// Persists a registered client and its classification atomically.
    ///
    /// @param definition the registered client and its classification
    void save(RegisteredClientDefinition definition);

    /// Persists a client submitted through the Spring Authorization Server contract as a user-managed client.
    @Override
    default void save(RegisteredClient registeredClient) {
        this.save(new RegisteredClientDefinition(registeredClient, RegisteredClientType.USER));
    }

    /// Finds a registered client together with its classification.
    ///
    /// @param id the persistent registered-client identifier
    /// @return the complete definition, or `null` when no client exists
    @Nullable
    RegisteredClientDefinition findDefinitionById(String id);

    /// Finds a registered client by its OAuth client identifier together with its classification.
    ///
    /// @param clientId the OAuth client identifier
    /// @return the complete definition, or `null` when no client exists
    @Nullable
    RegisteredClientDefinition findDefinitionByClientId(String clientId);

    /// Deletes a registered client and its classification without touching OAuth state.
    ///
    /// @param id the persistent registered-client identifier
    void deleteById(String id);
}

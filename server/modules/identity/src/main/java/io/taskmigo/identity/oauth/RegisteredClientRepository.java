package io.taskmigo.identity.oauth;

/// Defines the registered-client persistence contract used by Taskmigo integrations.
public interface RegisteredClientRepository
    extends org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
{
    /// Deletes only the registered-client row for the supplied persistent identifier.
    ///
    /// Authorization and consent state are deliberately retained by the migration contract.
    ///
    /// @param id the persistent registered-client identifier
    void deleteById(String id);
}

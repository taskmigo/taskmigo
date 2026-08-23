package io.taskmigo.identity;

import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Repository;

@Repository
final class InternalClientRepository {

    private static final String INSERT_PERMISSION =
        "insert into oauth_service_principal_permissions(registered_client_id, permission_key) values (?, ?)";

    private final JdbcRegisteredClientRepository registeredClients;
    private final JdbcClient jdbc;
    private final JdbcOperations jdbcOperations;

    InternalClientRepository(
        JdbcRegisteredClientRepository registeredClients,
        JdbcClient jdbc,
        JdbcOperations jdbcOperations
    ) {
        this.registeredClients = registeredClients;
        this.jdbc = jdbc;
        this.jdbcOperations = jdbcOperations;
    }

    Optional<ManagedRegistration> findManagedRegistration(String registrationKey) {
        return jdbc
            .sql(
                """
                select registered_client_id, configuration_version, definition_hash
                from oauth_client_management
                where registration_key = :registrationKey
                """
            )
            .param("registrationKey", registrationKey)
            .query((result, rowNumber) ->
                new ManagedRegistration(result.getString(1), result.getLong(2), result.getString(3))
            )
            .optional();
    }

    @Nullable
    RegisteredClient findById(String id) {
        return registeredClients.findById(id);
    }

    @Nullable
    RegisteredClient findByClientId(String clientId) {
        return registeredClients.findByClientId(clientId);
    }

    void save(RegisteredClient client) {
        registeredClients.save(client);
    }

    void saveManagement(
        String registrationKey,
        InternalClientDefinition definition,
        String definitionHash,
        String registeredClientId
    ) {
        jdbc.sql(
            """
            insert into oauth_client_management(
                registered_client_id, registration_key, client_type, trust_level, managed_by,
                enabled, configuration_version, definition_hash, created_at, updated_at
            ) values (:clientId, :registrationKey, 'CONFIDENTIAL', 'FIRST_PARTY', 'SYSTEM',
                      :enabled, :configurationVersion, :definitionHash, current_timestamp, current_timestamp)
            on conflict (registered_client_id) do update set
                enabled = excluded.enabled,
                configuration_version = excluded.configuration_version,
                definition_hash = excluded.definition_hash,
                updated_at = current_timestamp
            """
        )
            .param("clientId", registeredClientId)
            .param("registrationKey", registrationKey)
            .param("enabled", definition.enabled())
            .param("configurationVersion", definition.configurationVersion())
            .param("definitionHash", definitionHash)
            .update();
    }

    void replaceServicePrincipal(InternalClientDefinition definition, String registeredClientId) {
        jdbc.sql("delete from oauth_service_principal_permissions where registered_client_id = :clientId")
            .param("clientId", registeredClientId)
            .update();
        if (definition.servicePermissions().isEmpty()) {
            jdbc.sql("delete from oauth_service_principal where registered_client_id = :clientId")
                .param("clientId", registeredClientId)
                .update();
            return;
        }

        jdbc.sql(
            """
            insert into oauth_service_principal(registered_client_id, enabled) values (:clientId, :enabled)
            on conflict (registered_client_id) do update set enabled = excluded.enabled
            """
        )
            .param("clientId", registeredClientId)
            .param("enabled", definition.enabled())
            .update();

        var batch = definition
            .servicePermissions()
            .stream()
            .sorted()
            .map(permission -> new Object[] { registeredClientId, permission })
            .toList();
        jdbcOperations.batchUpdate(INSERT_PERMISSION, batch);
    }

    record ManagedRegistration(String registeredClientId, long configurationVersion, String definitionHash) {}
}

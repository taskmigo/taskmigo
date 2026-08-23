package io.taskmigo.web.security;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class InternalClientReconciler implements ApplicationRunner {

    private static final String RECONCILIATION_LOCK = "select pg_advisory_xact_lock(827319409)";

    private final SecurityProperties properties;
    private final JdbcRegisteredClientRepository clients;
    private final JdbcOperations jdbc;
    private final PasswordEncoder passwordEncoder;

    InternalClientReconciler(
        SecurityProperties properties,
        JdbcRegisteredClientRepository clients,
        JdbcOperations jdbc,
        PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.clients = clients;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        jdbc.execute(RECONCILIATION_LOCK);
        properties
            .internalClients()
            .entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> reconcile(entry.getKey(), entry.getValue()));
    }

    private void reconcile(String registrationKey, SecurityProperties.InternalClientProperties definition) {
        ClientType clientType = validate(registrationKey, definition);
        @Nullable
        String managedClientId = findManagedClientId(registrationKey);
        @Nullable
        RegisteredClient existing = managedClientId == null ? null : clients.findById(managedClientId);

        if (managedClientId != null && existing == null) {
            throw new IllegalStateException("Managed OAuth client is missing: " + registrationKey);
        }
        if (existing != null && !existing.getClientId().equals(definition.clientId())) {
            throw new IllegalStateException("client-id is immutable for internal client: " + registrationKey);
        }
        if (
            existing != null &&
            existing.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) !=
                (clientType == ClientType.CONFIDENTIAL)
        ) {
            throw new IllegalStateException("client-type is immutable for internal client: " + registrationKey);
        }
        if (existing == null && clients.findByClientId(definition.clientId()) != null) {
            throw new IllegalStateException("Refusing to adopt unmanaged OAuth client: " + definition.clientId());
        }

        RegisteredClient desired = desiredClient(registrationKey, definition, clientType, existing);
        clients.save(desired);
        saveManagementMetadata(registrationKey, definition, clientType, desired.getId());
        saveServicePrincipal(definition, desired.getId());
    }

    private ClientType validate(String registrationKey, SecurityProperties.InternalClientProperties definition) {
        if (!StringUtils.hasText(registrationKey)) throw new IllegalStateException(
            "Internal client key must not be blank"
        );
        if (!StringUtils.hasText(definition.clientId())) {
            throw new IllegalStateException("client-id must not be blank for internal client: " + registrationKey);
        }
        if (definition.grantTypes().isEmpty()) {
            throw new IllegalStateException("grant-types must not be empty for internal client: " + registrationKey);
        }
        if (definition.scopes().isEmpty()) {
            throw new IllegalStateException("scopes must not be empty for internal client: " + registrationKey);
        }

        ClientType clientType;
        try {
            clientType = ClientType.valueOf(definition.clientType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Unsupported client-type for internal client: " + registrationKey,
                exception
            );
        }

        boolean hasSecret = StringUtils.hasText(definition.clientSecret());
        if (clientType == ClientType.CONFIDENTIAL && !hasSecret) {
            throw new IllegalStateException(
                "client-secret is required for confidential internal client: " + registrationKey
            );
        }
        if (clientType == ClientType.PUBLIC && hasSecret) {
            throw new IllegalStateException("Public internal client must not have a client-secret: " + registrationKey);
        }
        if (
            clientType == ClientType.PUBLIC &&
            definition.grantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue())
        ) {
            throw new IllegalStateException("Public internal client cannot use client_credentials: " + registrationKey);
        }
        if (
            clientType == ClientType.PUBLIC &&
            definition.grantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE.getValue()) &&
            !definition.requireProofKey()
        ) {
            throw new IllegalStateException("Public authorization-code client must require PKCE: " + registrationKey);
        }

        Set<String> unknownPermissions = new java.util.HashSet<>(definition.servicePermissions());
        unknownPermissions.removeAll(ServicePrincipalPermissions.ALL);
        if (!unknownPermissions.isEmpty()) {
            throw new IllegalStateException(
                "Unknown service permissions for " + registrationKey + ": " + unknownPermissions
            );
        }
        if (
            !definition.servicePermissions().isEmpty() &&
            !definition.grantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue())
        ) {
            throw new IllegalStateException("Service permissions require client_credentials: " + registrationKey);
        }
        return clientType;
    }

    private RegisteredClient desiredClient(
        String registrationKey,
        SecurityProperties.InternalClientProperties definition,
        ClientType clientType,
        @Nullable RegisteredClient existing
    ) {
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(UUID.randomUUID().toString()).clientId(definition.clientId())
                : RegisteredClient.from(existing);

        @Nullable
        String encodedSecret = encodedSecret(definition, clientType, existing);
        if (encodedSecret != null) builder.clientSecret(encodedSecret);
        List<AuthorizationGrantType> grantTypes = new ArrayList<>();
        definition.grantTypes().forEach(value -> grantTypes.add(grantType(value, registrationKey)));

        return builder
            .clientName("Internal " + registrationKey)
            .clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.add(
                    clientType == ClientType.CONFIDENTIAL
                        ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                        : ClientAuthenticationMethod.NONE
                );
            })
            .authorizationGrantTypes(types -> {
                types.clear();
                types.addAll(grantTypes);
            })
            .redirectUris(uris -> {
                uris.clear();
                uris.addAll(definition.redirectUris());
            })
            .postLogoutRedirectUris(uris -> {
                uris.clear();
                uris.addAll(definition.postLogoutRedirectUris());
            })
            .scopes(scopes -> {
                scopes.clear();
                scopes.addAll(definition.scopes());
            })
            .clientSettings(
                ClientSettings.builder()
                    .requireProofKey(definition.requireProofKey())
                    .requireAuthorizationConsent(definition.requireAuthorizationConsent())
                    .build()
            )
            .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
            .build();
    }

    private @Nullable String encodedSecret(
        SecurityProperties.InternalClientProperties definition,
        ClientType clientType,
        @Nullable RegisteredClient existing
    ) {
        if (clientType == ClientType.PUBLIC) return null;
        @Nullable
        String secret = definition.clientSecret();
        if (secret == null) throw new IllegalStateException("Confidential client-secret must not be null");
        @Nullable
        String existingSecret = existing == null ? null : existing.getClientSecret();
        return existingSecret != null && passwordEncoder.matches(secret, existingSecret)
            ? existingSecret
            : passwordEncoder.encode(secret);
    }

    private AuthorizationGrantType grantType(String value, String registrationKey) {
        return switch (value) {
            case "authorization_code" -> AuthorizationGrantType.AUTHORIZATION_CODE;
            case "client_credentials" -> AuthorizationGrantType.CLIENT_CREDENTIALS;
            case "refresh_token" -> AuthorizationGrantType.REFRESH_TOKEN;
            default -> throw new IllegalStateException(
                "Unsupported grant-type for internal client " + registrationKey + ": " + value
            );
        };
    }

    private @Nullable String findManagedClientId(String registrationKey) {
        List<String> ids = jdbc.queryForList(
            "select registered_client_id from oauth_client_management where registration_key = ?",
            String.class,
            registrationKey
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void saveManagementMetadata(
        String registrationKey,
        SecurityProperties.InternalClientProperties definition,
        ClientType clientType,
        String registeredClientId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update(
            """
            insert into oauth_client_management(
                registered_client_id, registration_key, client_type, trust_level, managed_by,
                enabled, created_at, updated_at
            ) values (?, ?, ?, 'FIRST_PARTY', 'SYSTEM', ?, ?, ?)
            on conflict (registered_client_id) do update set
                client_type = excluded.client_type,
                trust_level = excluded.trust_level,
                managed_by = excluded.managed_by,
                enabled = excluded.enabled,
                updated_at = excluded.updated_at
            """,
            registeredClientId,
            registrationKey,
            clientType.name(),
            definition.enabled(),
            now,
            now
        );
    }

    private void saveServicePrincipal(
        SecurityProperties.InternalClientProperties definition,
        String registeredClientId
    ) {
        jdbc.update(
            "delete from oauth_service_principal_permissions where registered_client_id = ?",
            registeredClientId
        );
        if (definition.servicePermissions().isEmpty()) {
            jdbc.update("delete from oauth_service_principal where registered_client_id = ?", registeredClientId);
            return;
        }

        jdbc.update(
            """
            insert into oauth_service_principal(registered_client_id, enabled) values (?, ?)
            on conflict (registered_client_id) do update set enabled = excluded.enabled
            """,
            registeredClientId,
            definition.enabled()
        );
        definition
            .servicePermissions()
            .stream()
            .sorted()
            .forEach(permission ->
                jdbc.update(
                    "insert into oauth_service_principal_permissions(registered_client_id, permission_key) values (?, ?)",
                    registeredClientId,
                    permission
                )
            );
    }

    private enum ClientType {
        PUBLIC,
        CONFIDENTIAL,
    }
}

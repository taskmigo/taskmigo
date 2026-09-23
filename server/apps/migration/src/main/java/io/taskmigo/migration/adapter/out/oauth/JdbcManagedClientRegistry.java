package io.taskmigo.migration.adapter.out.oauth;

import io.taskmigo.migration.application.model.ManagedOAuthClient;
import io.taskmigo.migration.application.port.out.ManagedClientRegistry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/// Adapts migration-managed OAuth client persistence to Spring Authorization Server JDBC storage.
@Component
final class JdbcManagedClientRegistry implements ManagedClientRegistry {

    private final JdbcRegisteredClientRepository clients;

    JdbcManagedClientRegistry(JdbcRegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public Optional<ExistingClient> findByClientId(String clientId) {
        RegisteredClient client = this.clients.findByClientId(clientId);
        if (client == null) {
            return Optional.empty();
        }
        return Optional.of(new ExistingClient(client.getId(), client.getClientSecret(), InternalClientMetadata.isManaged(client)));
    }

    @Override
    public boolean matches(ManagedOAuthClient desired) {
        RegisteredClient current = this.clients.findByClientId(desired.clientId());
        if (current == null) {
            return false;
        }
        RegisteredClient requested = registeredClient(desired, current);
        return sameManagedData(current, requested);
    }

    @Override
    public void save(ManagedOAuthClient desired) {
        RegisteredClient current = this.clients.findByClientId(desired.clientId());
        this.clients.save(registeredClient(desired, current));
    }

    private static RegisteredClient registeredClient(
        ManagedOAuthClient client,
        @Nullable RegisteredClient existing
    ) {
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(client.id()).clientId(client.clientId())
                : RegisteredClient.from(existing).clientId(client.clientId());

        return builder
            .clientSecret(client.encodedSecret())
            .clientName(client.clientName())
            .clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.addAll(
                    client
                        .clientAuthenticationMethods()
                        .stream()
                        .map(ClientAuthenticationMethod::new)
                        .collect(Collectors.toSet())
                );
            })
            .authorizationGrantTypes(grants -> {
                grants.clear();
                grants.addAll(
                    client
                        .authorizationGrantTypes()
                        .stream()
                        .map(AuthorizationGrantType::new)
                        .collect(Collectors.toSet())
                );
            })
            .redirectUris(uris -> {
                uris.clear();
                uris.addAll(client.redirectUris());
            })
            .postLogoutRedirectUris(uris -> {
                uris.clear();
                uris.addAll(client.postLogoutRedirectUris());
            })
            .scopes(scopes -> {
                scopes.clear();
                scopes.addAll(client.scopes());
            })
            .clientSettings(InternalClientMetadata.settings(client))
            .tokenSettings(tokenSettings(client))
            .build();
    }

    private static TokenSettings tokenSettings(ManagedOAuthClient client) {
        return TokenSettings.builder()
            .authorizationCodeTimeToLive(client.authorizationCodeTimeToLive())
            .accessTokenTimeToLive(client.accessTokenTimeToLive())
            .accessTokenFormat(new OAuth2TokenFormat(client.accessTokenFormat()))
            .deviceCodeTimeToLive(client.deviceCodeTimeToLive())
            .reuseRefreshTokens(client.reuseRefreshTokens())
            .refreshTokenTimeToLive(client.refreshTokenTimeToLive())
            .idTokenSignatureAlgorithm(
                Objects.requireNonNull(
                    SignatureAlgorithm.from(client.idTokenSignatureAlgorithm()),
                    "Unsupported ID token signature algorithm"
                )
            )
            .build();
    }

    private static boolean sameManagedData(RegisteredClient current, RegisteredClient requested) {
        return (
            Objects.equals(current.getClientId(), requested.getClientId()) &&
            Objects.equals(current.getClientSecret(), requested.getClientSecret()) &&
            Objects.equals(current.getClientName(), requested.getClientName()) &&
            Objects.equals(current.getClientAuthenticationMethods(), requested.getClientAuthenticationMethods()) &&
            Objects.equals(current.getAuthorizationGrantTypes(), requested.getAuthorizationGrantTypes()) &&
            Objects.equals(current.getRedirectUris(), requested.getRedirectUris()) &&
            Objects.equals(current.getPostLogoutRedirectUris(), requested.getPostLogoutRedirectUris()) &&
            Objects.equals(current.getScopes(), requested.getScopes()) &&
            Objects.equals(current.getClientSettings(), requested.getClientSettings()) &&
            Objects.equals(current.getTokenSettings(), requested.getTokenSettings())
        );
    }
}

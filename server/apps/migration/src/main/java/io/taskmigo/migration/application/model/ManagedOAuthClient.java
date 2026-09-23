package io.taskmigo.migration.application.model;

import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Represents the complete desired persisted state of one migration-managed OAuth client.
public record ManagedOAuthClient(
    String id,
    String clientId,
    String encodedSecret,
    String clientName,
    Set<String> clientAuthenticationMethods,
    Set<String> authorizationGrantTypes,
    Set<String> redirectUris,
    Set<String> postLogoutRedirectUris,
    Set<String> scopes,
    boolean requireProofKey,
    boolean requireAuthorizationConsent,
    @Nullable String jwkSetUri,
    @Nullable String tokenEndpointAuthenticationSigningAlgorithm,
    Duration authorizationCodeTimeToLive,
    Duration accessTokenTimeToLive,
    String accessTokenFormat,
    Duration deviceCodeTimeToLive,
    boolean reuseRefreshTokens,
    Duration refreshTokenTimeToLive,
    String idTokenSignatureAlgorithm
) {
    public ManagedOAuthClient {
        clientAuthenticationMethods = Set.copyOf(clientAuthenticationMethods);
        authorizationGrantTypes = Set.copyOf(authorizationGrantTypes);
        redirectUris = Set.copyOf(redirectUris);
        postLogoutRedirectUris = Set.copyOf(postLogoutRedirectUris);
        scopes = Set.copyOf(scopes);
    }

    /// Creates desired persisted state from one installation definition and an encoded secret.
    ///
    /// @param id persistent registration identifier
    /// @param encodedSecret encoded client secret
    /// @param definition desired installation definition
    /// @return desired persisted OAuth client state
    public static ManagedOAuthClient from(
        String id,
        String encodedSecret,
        InstallationPlan.OAuthClient definition
    ) {
        return new ManagedOAuthClient(
            id,
            definition.clientId(),
            encodedSecret,
            definition.clientName(),
            definition.clientAuthenticationMethods(),
            definition.authorizationGrantTypes(),
            definition.redirectUris(),
            definition.postLogoutRedirectUris(),
            definition.scopes(),
            definition.requireProofKey(),
            definition.requireAuthorizationConsent(),
            definition.jwkSetUri(),
            definition.tokenEndpointAuthenticationSigningAlgorithm(),
            definition.authorizationCodeTimeToLive(),
            definition.accessTokenTimeToLive(),
            definition.accessTokenFormat(),
            definition.deviceCodeTimeToLive(),
            definition.reuseRefreshTokens(),
            definition.refreshTokenTimeToLive(),
            definition.idTokenSignatureAlgorithm()
        );
    }
}

package io.taskmigo.identity.persistence.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "oauth2_registered_client")
@SuppressWarnings("NotNullFieldNotInitialized")
final class RegisteredClientEntity {

    @Id
    @Column(length = 100)
    String id;

    @Column(name = "client_id", nullable = false, length = 100)
    String clientId;

    @Column(name = "client_id_issued_at", nullable = false)
    Instant clientIdIssuedAt;

    @Nullable
    @Column(name = "client_secret", length = 200)
    String clientSecret;

    @Nullable
    @Column(name = "client_secret_expires_at")
    Instant clientSecretExpiresAt;

    @Column(name = "client_name", nullable = false, length = 200)
    String clientName;

    @Column(name = "client_authentication_methods", nullable = false, length = 1000)
    String clientAuthenticationMethods;

    @Column(name = "authorization_grant_types", nullable = false, length = 1000)
    String authorizationGrantTypes;

    @Nullable
    @Column(name = "redirect_uris", length = 1000)
    String redirectUris;

    @Nullable
    @Column(name = "post_logout_redirect_uris", length = 1000)
    String postLogoutRedirectUris;

    @Column(nullable = false, length = 1000)
    String scopes;

    @Column(name = "client_settings", nullable = false, length = 2000)
    String clientSettings;

    @Column(name = "token_settings", nullable = false, length = 2000)
    String tokenSettings;

    protected RegisteredClientEntity() {}
}

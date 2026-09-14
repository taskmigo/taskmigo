package io.taskmigo.identity.persistence.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "oauth2_authorization")
@SuppressWarnings("NotNullFieldNotInitialized")
final class OAuth2AuthorizationEntity {

    @Id
    @Column(length = 100)
    String id;

    @Column(name = "registered_client_id", nullable = false, length = 100)
    String registeredClientId;

    @Column(name = "principal_name", nullable = false, length = 200)
    String principalName;

    @Column(name = "authorization_grant_type", nullable = false, length = 100)
    String authorizationGrantType;

    @Nullable
    @Column(name = "authorized_scopes", length = 1000)
    String authorizedScopes;

    @Nullable
    @Column(columnDefinition = "text")
    String attributes;

    @Nullable
    @Column(length = 500)
    String state;

    @Nullable
    @Column(name = "authorization_code_value", columnDefinition = "text")
    String authorizationCodeValue;

    @Nullable
    @Column(name = "authorization_code_issued_at")
    Instant authorizationCodeIssuedAt;

    @Nullable
    @Column(name = "authorization_code_expires_at")
    Instant authorizationCodeExpiresAt;

    @Nullable
    @Column(name = "authorization_code_metadata", columnDefinition = "text")
    String authorizationCodeMetadata;

    @Nullable
    @Column(name = "access_token_value", columnDefinition = "text")
    String accessTokenValue;

    @Nullable
    @Column(name = "access_token_issued_at")
    Instant accessTokenIssuedAt;

    @Nullable
    @Column(name = "access_token_expires_at")
    Instant accessTokenExpiresAt;

    @Nullable
    @Column(name = "access_token_metadata", columnDefinition = "text")
    String accessTokenMetadata;

    @Nullable
    @Column(name = "access_token_type", length = 100)
    String accessTokenType;

    @Nullable
    @Column(name = "access_token_scopes", length = 1000)
    String accessTokenScopes;

    @Nullable
    @Column(name = "oidc_id_token_value", columnDefinition = "text")
    String oidcIdTokenValue;

    @Nullable
    @Column(name = "oidc_id_token_issued_at")
    Instant oidcIdTokenIssuedAt;

    @Nullable
    @Column(name = "oidc_id_token_expires_at")
    Instant oidcIdTokenExpiresAt;

    @Nullable
    @Column(name = "oidc_id_token_metadata", columnDefinition = "text")
    String oidcIdTokenMetadata;

    @Nullable
    @Column(name = "refresh_token_value", columnDefinition = "text")
    String refreshTokenValue;

    @Nullable
    @Column(name = "refresh_token_issued_at")
    Instant refreshTokenIssuedAt;

    @Nullable
    @Column(name = "refresh_token_expires_at")
    Instant refreshTokenExpiresAt;

    @Nullable
    @Column(name = "refresh_token_metadata", columnDefinition = "text")
    String refreshTokenMetadata;

    @Nullable
    @Column(name = "user_code_value", columnDefinition = "text")
    String userCodeValue;

    @Nullable
    @Column(name = "user_code_issued_at")
    Instant userCodeIssuedAt;

    @Nullable
    @Column(name = "user_code_expires_at")
    Instant userCodeExpiresAt;

    @Nullable
    @Column(name = "user_code_metadata", columnDefinition = "text")
    String userCodeMetadata;

    @Nullable
    @Column(name = "device_code_value", columnDefinition = "text")
    String deviceCodeValue;

    @Nullable
    @Column(name = "device_code_issued_at")
    Instant deviceCodeIssuedAt;

    @Nullable
    @Column(name = "device_code_expires_at")
    Instant deviceCodeExpiresAt;

    @Nullable
    @Column(name = "device_code_metadata", columnDefinition = "text")
    String deviceCodeMetadata;

    protected OAuth2AuthorizationEntity() {}
}

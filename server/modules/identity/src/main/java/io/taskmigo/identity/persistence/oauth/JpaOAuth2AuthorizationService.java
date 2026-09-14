package io.taskmigo.identity.persistence.oauth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2DeviceCode;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/// Adapts Spring Authorization Server authorization state to the Taskmigo JPA schema.
@Component
public class JpaOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationEntityRepository repository;
    private final RegisteredClientRepository registeredClients;
    private final OAuthPersistenceCodec codec;

    JpaOAuth2AuthorizationService(
        OAuth2AuthorizationEntityRepository repository,
        RegisteredClientRepository registeredClients,
        OAuthPersistenceCodec codec
    ) {
        this.repository = repository;
        this.registeredClients = registeredClients;
        this.codec = codec;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        this.repository.save(this.toEntity(authorization));
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        Assert.notNull(authorization, "authorization cannot be null");
        this.repository.deleteById(authorization.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable OAuth2Authorization findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return this.repository.findById(id).map(this::toObject).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable OAuth2Authorization findByToken(String token, @Nullable OAuth2TokenType tokenType) {
        Assert.hasText(token, "token cannot be empty");
        Optional<OAuth2AuthorizationEntity> result = this.findEntityByToken(token, tokenType);
        return result.map(this::toObject).orElse(null);
    }

    private Optional<OAuth2AuthorizationEntity> findEntityByToken(String token, @Nullable OAuth2TokenType tokenType) {
        if (tokenType == null) {
            return this.repository
                .findByState(token)
                .or(() -> this.repository.findByAuthorizationCodeValue(token))
                .or(() -> this.repository.findByAccessTokenValue(token))
                .or(() -> this.repository.findByOidcIdTokenValue(token))
                .or(() -> this.repository.findByRefreshTokenValue(token))
                .or(() -> this.repository.findByUserCodeValue(token))
                .or(() -> this.repository.findByDeviceCodeValue(token));
        }
        return switch (tokenType.getValue()) {
            case OAuth2ParameterNames.STATE -> this.repository.findByState(token);
            case OAuth2ParameterNames.CODE -> this.repository.findByAuthorizationCodeValue(token);
            case OAuth2ParameterNames.ACCESS_TOKEN -> this.repository.findByAccessTokenValue(token);
            case OAuth2ParameterNames.REFRESH_TOKEN -> this.repository.findByRefreshTokenValue(token);
            case OidcParameterNames.ID_TOKEN -> this.repository.findByOidcIdTokenValue(token);
            case OAuth2ParameterNames.USER_CODE -> this.repository.findByUserCodeValue(token);
            case OAuth2ParameterNames.DEVICE_CODE -> this.repository.findByDeviceCodeValue(token);
            default -> Optional.empty();
        };
    }

    private OAuth2Authorization toObject(OAuth2AuthorizationEntity entity) {
        RegisteredClient registeredClient = this.registeredClients.findById(entity.registeredClientId);
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                "The RegisteredClient with id '" + entity.registeredClientId + "' was not found"
            );
        }

        OAuth2Authorization.Builder builder = OAuth2Authorization.withRegisteredClient(registeredClient)
            .id(entity.id)
            .principalName(entity.principalName)
            .authorizationGrantType(this.codec.authorizationGrantType(entity.authorizationGrantType))
            .authorizedScopes(this.codec.readDelimited(entity.authorizedScopes))
            .attributes(attributes -> attributes.putAll(this.codec.readMap(entity.attributes)));
        if (StringUtils.hasText(entity.state)) {
            builder.attribute(OAuth2ParameterNames.STATE, entity.state);
        }
        this.addAuthorizationCode(builder, entity);
        this.addAccessToken(builder, entity);
        this.addOidcIdToken(builder, entity);
        this.addRefreshToken(builder, entity);
        this.addUserCode(builder, entity);
        this.addDeviceCode(builder, entity);
        return builder.build();
    }

    private void addAuthorizationCode(OAuth2Authorization.Builder builder, OAuth2AuthorizationEntity entity) {
        if (entity.authorizationCodeValue == null) {
            return;
        }
        OAuth2AuthorizationCode token = new OAuth2AuthorizationCode(
            entity.authorizationCodeValue,
            required(entity.authorizationCodeIssuedAt, "authorization code issued_at"),
            required(entity.authorizationCodeExpiresAt, "authorization code expires_at")
        );
        builder.token(token, metadata -> metadata.putAll(this.codec.readMap(entity.authorizationCodeMetadata)));
    }

    private void addAccessToken(OAuth2Authorization.Builder builder, OAuth2AuthorizationEntity entity) {
        if (entity.accessTokenValue == null) {
            return;
        }
        OAuth2AccessToken token = new OAuth2AccessToken(
            accessTokenType(entity.accessTokenType),
            entity.accessTokenValue,
            required(entity.accessTokenIssuedAt, "access token issued_at"),
            required(entity.accessTokenExpiresAt, "access token expires_at"),
            this.codec.readDelimited(entity.accessTokenScopes)
        );
        builder.token(token, metadata -> metadata.putAll(this.codec.readMap(entity.accessTokenMetadata)));
    }

    private void addOidcIdToken(OAuth2Authorization.Builder builder, OAuth2AuthorizationEntity entity) {
        if (entity.oidcIdTokenValue == null) {
            return;
        }
        Map<String, Object> metadata = this.codec.readMap(entity.oidcIdTokenMetadata);
        Object claimsValue = metadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = claimsValue instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        OidcIdToken token = new OidcIdToken(
            entity.oidcIdTokenValue,
            required(entity.oidcIdTokenIssuedAt, "OIDC ID token issued_at"),
            required(entity.oidcIdTokenExpiresAt, "OIDC ID token expires_at"),
            claims
        );
        builder.token(token, values -> values.putAll(metadata));
    }

    private void addRefreshToken(OAuth2Authorization.Builder builder, OAuth2AuthorizationEntity entity) {
        if (entity.refreshTokenValue == null) {
            return;
        }
        OAuth2RefreshToken token = new OAuth2RefreshToken(
            entity.refreshTokenValue,
            required(entity.refreshTokenIssuedAt, "refresh token issued_at"),
            entity.refreshTokenExpiresAt
        );
        builder.token(token, metadata -> metadata.putAll(this.codec.readMap(entity.refreshTokenMetadata)));
    }

    private void addUserCode(OAuth2Authorization.Builder builder, OAuth2AuthorizationEntity entity) {
        if (entity.userCodeValue == null) {
            return;
        }
        OAuth2UserCode token = new OAuth2UserCode(
            entity.userCodeValue,
            required(entity.userCodeIssuedAt, "user code issued_at"),
            required(entity.userCodeExpiresAt, "user code expires_at")
        );
        builder.token(token, metadata -> metadata.putAll(this.codec.readMap(entity.userCodeMetadata)));
    }

    private void addDeviceCode(OAuth2Authorization.Builder builder, OAuth2AuthorizationEntity entity) {
        if (entity.deviceCodeValue == null) {
            return;
        }
        OAuth2DeviceCode token = new OAuth2DeviceCode(
            entity.deviceCodeValue,
            required(entity.deviceCodeIssuedAt, "device code issued_at"),
            required(entity.deviceCodeExpiresAt, "device code expires_at")
        );
        builder.token(token, metadata -> metadata.putAll(this.codec.readMap(entity.deviceCodeMetadata)));
    }

    private OAuth2AuthorizationEntity toEntity(OAuth2Authorization authorization) {
        OAuth2AuthorizationEntity entity = new OAuth2AuthorizationEntity();
        entity.id = authorization.getId();
        entity.registeredClientId = authorization.getRegisteredClientId();
        entity.principalName = authorization.getPrincipalName();
        entity.authorizationGrantType = authorization.getAuthorizationGrantType().getValue();
        entity.authorizedScopes = this.codec.writeNullableDelimited(authorization.getAuthorizedScopes());
        entity.attributes = this.codec.writeMap(authorization.getAttributes());
        Object state = authorization.getAttribute(OAuth2ParameterNames.STATE);
        entity.state = state instanceof String string ? string : null;

        OAuth2Authorization.Token<OAuth2AuthorizationCode> authorizationCode = authorization.getToken(
            OAuth2AuthorizationCode.class
        );
        this.setTokenValues(
            authorizationCode,
            value -> entity.authorizationCodeValue = value,
            value -> entity.authorizationCodeIssuedAt = value,
            value -> entity.authorizationCodeExpiresAt = value,
            value -> entity.authorizationCodeMetadata = value
        );

        OAuth2Authorization.Token<OAuth2AccessToken> accessToken = authorization.getToken(OAuth2AccessToken.class);
        this.setTokenValues(
            accessToken,
            value -> entity.accessTokenValue = value,
            value -> entity.accessTokenIssuedAt = value,
            value -> entity.accessTokenExpiresAt = value,
            value -> entity.accessTokenMetadata = value
        );
        if (accessToken != null) {
            entity.accessTokenType = accessToken.getToken().getTokenType().getValue();
            entity.accessTokenScopes = this.codec.writeNullableDelimited(accessToken.getToken().getScopes());
        }

        OAuth2Authorization.Token<OidcIdToken> oidcIdToken = authorization.getToken(OidcIdToken.class);
        this.setTokenValues(
            oidcIdToken,
            value -> entity.oidcIdTokenValue = value,
            value -> entity.oidcIdTokenIssuedAt = value,
            value -> entity.oidcIdTokenExpiresAt = value,
            value -> entity.oidcIdTokenMetadata = value
        );

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getToken(OAuth2RefreshToken.class);
        this.setTokenValues(
            refreshToken,
            value -> entity.refreshTokenValue = value,
            value -> entity.refreshTokenIssuedAt = value,
            value -> entity.refreshTokenExpiresAt = value,
            value -> entity.refreshTokenMetadata = value
        );

        OAuth2Authorization.Token<OAuth2UserCode> userCode = authorization.getToken(OAuth2UserCode.class);
        this.setTokenValues(
            userCode,
            value -> entity.userCodeValue = value,
            value -> entity.userCodeIssuedAt = value,
            value -> entity.userCodeExpiresAt = value,
            value -> entity.userCodeMetadata = value
        );

        OAuth2Authorization.Token<OAuth2DeviceCode> deviceCode = authorization.getToken(OAuth2DeviceCode.class);
        this.setTokenValues(
            deviceCode,
            value -> entity.deviceCodeValue = value,
            value -> entity.deviceCodeIssuedAt = value,
            value -> entity.deviceCodeExpiresAt = value,
            value -> entity.deviceCodeMetadata = value
        );
        return entity;
    }

    private void setTokenValues(
        OAuth2Authorization.@Nullable Token<? extends OAuth2Token> token,
        Consumer<@Nullable String> valueConsumer,
        Consumer<@Nullable Instant> issuedAtConsumer,
        Consumer<@Nullable Instant> expiresAtConsumer,
        Consumer<@Nullable String> metadataConsumer
    ) {
        if (token == null) {
            valueConsumer.accept(null);
            issuedAtConsumer.accept(null);
            expiresAtConsumer.accept(null);
            metadataConsumer.accept(null);
            return;
        }
        OAuth2Token value = token.getToken();
        valueConsumer.accept(value.getTokenValue());
        issuedAtConsumer.accept(value.getIssuedAt());
        expiresAtConsumer.accept(value.getExpiresAt());
        metadataConsumer.accept(this.codec.writeMap(token.getMetadata()));
    }

    private static OAuth2AccessToken.TokenType accessTokenType(@Nullable String value) {
        if (OAuth2AccessToken.TokenType.BEARER.getValue().equalsIgnoreCase(value)) {
            return OAuth2AccessToken.TokenType.BEARER;
        }
        if (OAuth2AccessToken.TokenType.DPOP.getValue().equalsIgnoreCase(value)) {
            return OAuth2AccessToken.TokenType.DPOP;
        }
        throw new IllegalArgumentException("Unsupported OAuth access token type: " + value);
    }

    private static <T> T required(@Nullable T value, String field) {
        if (value == null) {
            throw new DataRetrievalFailureException("OAuth token is missing " + field);
        }
        return value;
    }
}

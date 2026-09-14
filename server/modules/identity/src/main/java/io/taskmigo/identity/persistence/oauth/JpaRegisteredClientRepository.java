package io.taskmigo.identity.persistence.oauth;

import io.taskmigo.identity.oauth.RegisteredClientDefinition;
import io.taskmigo.identity.oauth.RegisteredClientRepository;
import io.taskmigo.identity.oauth.RegisteredClientType;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.ConfigurationSettingNames;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/// Adapts Spring Authorization Server registered clients to the Taskmigo JPA schema.
@Component
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    private final RegisteredClientEntityRepository repository;
    private final RegisteredClientMetadataEntityRepository metadataRepository;
    private final OAuthPersistenceCodec codec;

    JpaRegisteredClientRepository(
        RegisteredClientEntityRepository repository,
        RegisteredClientMetadataEntityRepository metadataRepository,
        OAuthPersistenceCodec codec
    ) {
        this.repository = repository;
        this.metadataRepository = metadataRepository;
        this.codec = codec;
    }

    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        Assert.notNull(registeredClient, "registeredClient cannot be null");
        this.save(new RegisteredClientDefinition(registeredClient, RegisteredClientType.USER));
    }

    @Override
    @Transactional
    public void save(RegisteredClientDefinition definition) {
        Assert.notNull(definition, "definition cannot be null");
        this.repository.save(this.toEntity(definition.registeredClient()));
        this.metadataRepository.save(this.toMetadataEntity(definition));
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable RegisteredClient findById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return this.repository.findById(id).map(this::toObject).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable RegisteredClient findByClientId(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");
        return this.repository.findByClientId(clientId).map(this::toObject).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable RegisteredClientDefinition findDefinitionById(String id) {
        Assert.hasText(id, "id cannot be empty");
        return this.repository.findById(id).map(this::toDefinition).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable RegisteredClientDefinition findDefinitionByClientId(String clientId) {
        Assert.hasText(clientId, "clientId cannot be empty");
        return this.repository.findByClientId(clientId).map(this::toDefinition).orElse(null);
    }

    /// Deletes one registered client for the migration reconciler.
    ///
    /// This operation intentionally removes only the registered-client row. The
    /// migration contract does not clean up authorization or consent state.
    ///
    /// @param id the persistent registered-client identifier
    @Transactional
    public void deleteById(String id) {
        Assert.hasText(id, "id cannot be empty");
        this.metadataRepository.findById(id).ifPresent(this.metadataRepository::delete);
        this.repository.deleteById(id);
    }

    private RegisteredClientDefinition toDefinition(RegisteredClientEntity entity) {
        RegisteredClientMetadataEntity metadata = this.metadataRepository
            .findById(entity.id)
            .orElseThrow(() ->
                new DataRetrievalFailureException(
                    "Metadata for RegisteredClient with id '" + entity.id + "' was not found"
                )
            );
        return new RegisteredClientDefinition(
            this.toObject(entity),
            RegisteredClientType.requireValid(Objects.requireNonNull(metadata.type))
        );
    }

    private RegisteredClient toObject(RegisteredClientEntity entity) {
        RegisteredClient.Builder builder = RegisteredClient.withId(entity.id)
            .clientId(entity.clientId)
            .clientIdIssuedAt(entity.clientIdIssuedAt)
            .clientName(entity.clientName)
            .clientAuthenticationMethods(methods ->
                this.codec
                    .readDelimited(entity.clientAuthenticationMethods)
                    .stream()
                    .map(this.codec::clientAuthenticationMethod)
                    .forEach(methods::add)
            )
            .authorizationGrantTypes(grants ->
                this.codec
                    .readDelimited(entity.authorizationGrantTypes)
                    .stream()
                    .map(this.codec::authorizationGrantType)
                    .forEach(grants::add)
            )
            .redirectUris(uris -> uris.addAll(this.codec.readDelimited(entity.redirectUris)))
            .postLogoutRedirectUris(uris -> uris.addAll(this.codec.readDelimited(entity.postLogoutRedirectUris)))
            .scopes(scopes -> scopes.addAll(this.codec.readDelimited(entity.scopes)))
            .clientSettings(ClientSettings.withSettings(this.codec.readMap(entity.clientSettings)).build())
            .tokenSettings(this.tokenSettings(this.codec.readMap(entity.tokenSettings)));
        if (entity.clientSecret != null) {
            builder.clientSecret(entity.clientSecret);
        }
        if (entity.clientSecretExpiresAt != null) {
            builder.clientSecretExpiresAt(entity.clientSecretExpiresAt);
        }
        return builder.build();
    }

    private RegisteredClientEntity toEntity(RegisteredClient registeredClient) {
        RegisteredClientEntity entity = new RegisteredClientEntity();
        entity.id = registeredClient.getId();
        entity.clientId = registeredClient.getClientId();
        entity.clientIdIssuedAt = Objects.requireNonNullElseGet(registeredClient.getClientIdIssuedAt(), Instant::now);
        entity.clientSecret = registeredClient.getClientSecret();
        entity.clientSecretExpiresAt = registeredClient.getClientSecretExpiresAt();
        entity.clientName = registeredClient.getClientName();
        entity.clientAuthenticationMethods = this.codec.writeDelimited(
            registeredClient
                .getClientAuthenticationMethods()
                .stream()
                .map(ClientAuthenticationMethod::getValue)
                .toList()
        );
        entity.authorizationGrantTypes = this.codec.writeDelimited(
            registeredClient.getAuthorizationGrantTypes().stream().map(AuthorizationGrantType::getValue).toList()
        );
        entity.redirectUris = this.codec.writeNullableDelimited(registeredClient.getRedirectUris());
        entity.postLogoutRedirectUris = this.codec.writeNullableDelimited(registeredClient.getPostLogoutRedirectUris());
        entity.scopes = this.codec.writeDelimited(registeredClient.getScopes());
        entity.clientSettings = this.codec.writeMap(registeredClient.getClientSettings().getSettings());
        entity.tokenSettings = this.codec.writeMap(registeredClient.getTokenSettings().getSettings());
        return entity;
    }

    private RegisteredClientMetadataEntity toMetadataEntity(RegisteredClientDefinition definition) {
        RegisteredClientMetadataEntity entity = new RegisteredClientMetadataEntity();
        entity.registeredClientId = definition.registeredClient().getId();
        entity.type = definition.type();
        return entity;
    }

    private TokenSettings tokenSettings(Map<String, Object> values) {
        TokenSettings.Builder builder = TokenSettings.withSettings(values);
        if (!values.containsKey(ConfigurationSettingNames.Token.ACCESS_TOKEN_FORMAT)) {
            builder.accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED);
        }
        return builder.build();
    }
}

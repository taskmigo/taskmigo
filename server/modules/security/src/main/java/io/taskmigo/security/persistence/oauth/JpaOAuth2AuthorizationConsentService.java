package io.taskmigo.security.persistence.oauth;

import java.util.HashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/// Adapts Spring Authorization Server consent state to the Taskmigo JPA schema.
@Component
public class JpaOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private final OAuth2AuthorizationConsentEntityRepository repository;
    private final RegisteredClientRepository registeredClients;
    private final OAuthPersistenceCodec codec;

    JpaOAuth2AuthorizationConsentService(
        OAuth2AuthorizationConsentEntityRepository repository,
        RegisteredClientRepository registeredClients,
        OAuthPersistenceCodec codec
    ) {
        this.repository = repository;
        this.registeredClients = registeredClients;
        this.codec = codec;
    }

    @Override
    @Transactional
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        this.repository.save(this.toEntity(authorizationConsent));
    }

    @Override
    @Transactional
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent cannot be null");
        this.repository.deleteByRegisteredClientIdAndPrincipalName(
            authorizationConsent.getRegisteredClientId(),
            authorizationConsent.getPrincipalName()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public @Nullable OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        Assert.hasText(registeredClientId, "registeredClientId cannot be empty");
        Assert.hasText(principalName, "principalName cannot be empty");
        return this.repository
            .findByRegisteredClientIdAndPrincipalName(registeredClientId, principalName)
            .map(this::toObject)
            .orElse(null);
    }

    private OAuth2AuthorizationConsent toObject(OAuth2AuthorizationConsentEntity entity) {
        RegisteredClient registeredClient = this.registeredClients.findById(entity.registeredClientId);
        if (registeredClient == null) {
            throw new DataRetrievalFailureException(
                "The RegisteredClient with id '" + entity.registeredClientId + "' was not found"
            );
        }

        OAuth2AuthorizationConsent.Builder builder = OAuth2AuthorizationConsent.withId(
            registeredClient.getId(),
            entity.principalName
        );
        for (String authority : this.codec.readDelimited(entity.authorities)) {
            builder.authority(new SimpleGrantedAuthority(authority));
        }
        return builder.build();
    }

    private OAuth2AuthorizationConsentEntity toEntity(OAuth2AuthorizationConsent authorizationConsent) {
        OAuth2AuthorizationConsentEntity entity = new OAuth2AuthorizationConsentEntity();
        entity.registeredClientId = authorizationConsent.getRegisteredClientId();
        entity.principalName = authorizationConsent.getPrincipalName();
        Set<String> authorities = new HashSet<>();
        for (GrantedAuthority authority : authorizationConsent.getAuthorities()) {
            String authorityValue = authority.getAuthority();
            if (authorityValue != null) {
                authorities.add(authorityValue);
            }
        }
        entity.authorities = this.codec.writeDelimited(authorities);
        return entity;
    }
}

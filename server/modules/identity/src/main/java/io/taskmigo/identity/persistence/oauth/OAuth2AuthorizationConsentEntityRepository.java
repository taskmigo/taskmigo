package io.taskmigo.identity.persistence.oauth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface OAuth2AuthorizationConsentEntityRepository
    extends JpaRepository<OAuth2AuthorizationConsentEntity, OAuth2AuthorizationConsentEntity.ConsentId>
{
    Optional<OAuth2AuthorizationConsentEntity> findByRegisteredClientIdAndPrincipalName(
        String registeredClientId,
        String principalName
    );

    void deleteByRegisteredClientIdAndPrincipalName(String registeredClientId, String principalName);
}

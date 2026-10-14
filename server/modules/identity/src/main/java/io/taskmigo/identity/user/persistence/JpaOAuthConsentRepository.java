package io.taskmigo.identity.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/// Removes all clients' consent rows for one User identity.
interface JpaOAuthConsentRepository extends JpaRepository<OAuthConsentEntity, OAuthConsentEntity.ConsentId> {
    long deleteByIdPrincipalName(String principalName);
}

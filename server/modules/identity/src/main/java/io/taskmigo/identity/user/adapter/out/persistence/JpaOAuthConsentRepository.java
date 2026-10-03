package io.taskmigo.identity.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/// Removes all clients' consent rows for one User identity.
interface JpaOAuthConsentRepository extends JpaRepository<OAuthConsentEntity, OAuthConsentEntity.ConsentId> {
    long deleteByIdPrincipalName(String principalName);
}

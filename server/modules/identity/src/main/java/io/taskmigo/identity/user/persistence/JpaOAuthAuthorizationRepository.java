package io.taskmigo.identity.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/// Removes authorizations belonging to a User before its username is discarded.
interface JpaOAuthAuthorizationRepository extends JpaRepository<OAuthAuthorizationEntity, String> {
    long deleteByPrincipalName(String principalName);
}

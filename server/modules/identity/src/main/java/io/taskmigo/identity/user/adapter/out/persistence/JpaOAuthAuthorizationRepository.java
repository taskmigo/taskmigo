package io.taskmigo.identity.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/// Removes authorizations belonging to a User before its username is discarded.
interface JpaOAuthAuthorizationRepository extends JpaRepository<OAuthAuthorizationEntity, String> {
    long deleteByPrincipalName(String principalName);
}

package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.application.port.out.UserSessionStore;
import org.springframework.stereotype.Repository;

/// Removes User-owned OAuth state through JPA in the surrounding lifecycle transaction.
@Repository
class JpaUserSessionStore implements UserSessionStore {
    private final JpaOAuthAuthorizationRepository authorizations;
    private final JpaOAuthConsentRepository consents;

    JpaUserSessionStore(JpaOAuthAuthorizationRepository authorizations, JpaOAuthConsentRepository consents) {
        this.authorizations = authorizations;
        this.consents = consents;
    }

    @Override
    public boolean revoke(String username) {
        long removedGrants = this.authorizations.deleteByPrincipalName(username);
        long removedConsents = this.consents.deleteByIdPrincipalName(username);
        return removedGrants > 0 || removedConsents > 0;
    }
}

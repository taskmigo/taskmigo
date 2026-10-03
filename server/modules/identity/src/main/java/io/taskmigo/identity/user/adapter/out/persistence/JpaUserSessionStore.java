package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.application.port.out.UserSessionStore;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/// Removes User-owned HTTP sessions and OAuth state through JPA in the surrounding lifecycle transaction.
@Repository
class JpaUserSessionStore implements UserSessionStore {
    private final JpaHttpSessionRepository sessions;
    private final JpaOAuthAuthorizationRepository authorizations;
    private final JpaOAuthConsentRepository consents;

    JpaUserSessionStore(JpaHttpSessionRepository sessions, JpaOAuthAuthorizationRepository authorizations, JpaOAuthConsentRepository consents) {
        this.sessions = sessions;
        this.authorizations = authorizations;
        this.consents = consents;
    }

    @Override
    public boolean revoke(UUID userId, String username) {
        long removedSessions = this.sessions.deleteByPrincipalName(userId.toString());
        long removedGrants = this.authorizations.deleteByPrincipalName(username);
        long removedConsents = this.consents.deleteByIdPrincipalName(username);
        return removedSessions > 0 || removedGrants > 0 || removedConsents > 0;
    }
}

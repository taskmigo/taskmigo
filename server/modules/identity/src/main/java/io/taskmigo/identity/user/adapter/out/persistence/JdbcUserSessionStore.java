package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.application.port.out.UserSessionStore;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;

/// Removes OAuth authorization and consent rows before User identity data is discarded.
@Repository
public class JdbcUserSessionStore implements UserSessionStore {

    private final JdbcOperations jdbc;

    public JdbcUserSessionStore(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean revoke(String username) {
        int authorizations = this.jdbc.update(
            "delete from oauth2_authorization where principal_name = ?",
            username
        );
        int consents = this.jdbc.update(
            "delete from oauth2_authorization_consent where principal_name = ?",
            username
        );
        return authorizations > 0 || consents > 0;
    }
}

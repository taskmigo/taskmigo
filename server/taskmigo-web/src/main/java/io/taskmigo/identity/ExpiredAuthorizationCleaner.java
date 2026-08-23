package io.taskmigo.identity;

import java.time.Duration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ExpiredAuthorizationCleaner {

    private final IdentityProperties properties;
    private final JdbcClient jdbc;

    ExpiredAuthorizationCleaner(IdentityProperties properties, JdbcClient jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${taskmigo.security.authorization-cleanup.interval}")
    @Transactional
    public void clean() {
        Duration retention = properties.authorizationCleanup().retention();
        jdbc.sql(
            """
            delete from oauth2_authorization
            where authorization_grant_type = 'client_credentials'
              and access_token_expires_at < current_timestamp - make_interval(secs => :retentionSeconds)
            """
        )
            .param("retentionSeconds", retention.toSeconds())
            .update();
    }
}

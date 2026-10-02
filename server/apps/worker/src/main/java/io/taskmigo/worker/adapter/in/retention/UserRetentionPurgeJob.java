package io.taskmigo.worker.adapter.in.retention;

import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// Drives periodic User-retention purge through the Identity inbound port.
@Component
public final class UserRetentionPurgeJob {

    private final UserRetentionService retention;
    private final Clock clock;

    public UserRetentionPurgeJob(UserRetentionService retention) {
        this(retention, Clock.systemUTC());
    }

    UserRetentionPurgeJob(UserRetentionService retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${taskmigo.retention.purge-delay-ms:3600000}")
    void purgeExpiredUsers() {
        this.retention.purgeExpiredUsers(this.clock.instant());
    }
}

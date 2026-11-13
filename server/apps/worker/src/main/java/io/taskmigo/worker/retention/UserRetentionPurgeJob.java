package io.taskmigo.worker.retention;

import io.taskmigo.audit.context.AuditCause;
import io.taskmigo.audit.context.AuditContext;
import io.taskmigo.audit.context.AuditContexts;
import io.taskmigo.audit.context.AuditExecutor;
import io.taskmigo.identity.user.application.api.UserRetentionService;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// Drives the hourly User-retention tombstone job through the Identity User application API.
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

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.HOURS)
    void purgeExpiredUsers() {
        AuditContexts.run(
            AuditContext.system(
                "retention-purge",
                AuditExecutor.worker("worker"),
                AuditCause.scheduled("retention-purge-job")
            ),
            () -> this.retention.purgeExpiredUsers(this.clock.instant())
        );
    }
}

package io.taskmigo.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.audit.application.port.in.api.AuditEventService;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditFieldChange;
import io.taskmigo.audit.model.AuditMutationEvent;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AuditOutboxIntegrationTest extends ApiIntegrationTestSupport {

    private final AuditEventService events;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    AuditOutboxIntegrationTest(
        AuditEventService events,
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager
    ) {
        this.events = events;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Verifies Spring Modulith JobRunr enqueue participates in the originating business transaction.
     *
     * Given: an audit event published in a transaction that commits.
     * Expect: JobRunr contains durable work for that event even though the web app has no background worker.
     */
    @Test
    @DisplayName("persists durable audit work when transaction commits")
    void shouldPersistAuditJobWhenTransactionCommits() {
        // Arrange
        AuditMutationEvent event = event();

        // Act
        this.transactions.executeWithoutResult(__ -> this.events.publish(event));

        // Assert
        assertThat(this.jobCount(event.eventId())).isOne();
        assertThat(this.auditCount(event.eventId())).isZero();
    }

    /**
     * Verifies a rolled-back business transaction cannot leave audit work behind.
     *
     * Given: an audit event published before the surrounding transaction is marked rollback-only.
     * Expect: neither the JobRunr handoff nor the final audit row exists.
     */
    @Test
    @DisplayName("rolls back durable audit work with transaction")
    void shouldRollbackAuditJobWhenTransactionRollsBack() {
        // Arrange
        AuditMutationEvent event = event();

        // Act
        this.transactions.executeWithoutResult(status -> {
            this.events.publish(event);
            status.setRollbackOnly();
        });

        // Assert
        assertThat(this.jobCount(event.eventId())).isZero();
        assertThat(this.auditCount(event.eventId())).isZero();
    }

    private long jobCount(UUID eventId) {
        Long count = this.jdbc.queryForObject(
            "select count(*) from jobrunr_jobs where jobasjson like ?",
            Long.class,
            "%" + eventId + "%"
        );
        return count == null ? 0 : count;
    }

    private long auditCount(UUID eventId) {
        Long count = this.jdbc.queryForObject(
            "select count(*) from audit_logs where source_event_id = ?",
            Long.class,
            eventId
        );
        return count == null ? 0 : count;
    }

    private static AuditMutationEvent event() {
        return AuditMutationEvent.user(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.now(),
            List.of(AuditFieldChange.visible("firstName", "Old", "New"))
        );
    }
}

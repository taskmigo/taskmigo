package io.taskmigo.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.TaskmigoWorkerApplication;
import io.taskmigo.WorkerPostgresTestConfiguration;
import io.taskmigo.audit.application.port.in.api.AuditEventService;
import io.taskmigo.audit.application.port.in.api.AuditLogService;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditFieldChange;
import io.taskmigo.audit.model.AuditMutationEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    classes = TaskmigoWorkerApplication.class,
    properties = { "spring.flyway.enabled=true", "jobrunr.background-job-server.poll-interval-in-seconds=1" }
)
@Import(WorkerPostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditWorkerIntegrationTest {

    private final AuditEventService events;
    private final AuditLogService logs;
    private final TransactionTemplate transactions;

    AuditWorkerIntegrationTest(
        AuditEventService events,
        AuditLogService logs,
        PlatformTransactionManager transactionManager
    ) {
        this.events = events;
        this.logs = logs;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Verifies a worker instance consumes shared durable work and appends the immutable audit row.
     *
     * Given: an audit event committed to JobRunr storage.
     * Expect: the background server eventually appends exactly one queryable audit entry.
     */
    @Test
    @DisplayName("consumes durable audit work and appends audit log")
    void shouldAppendAuditLogWhenWorkerConsumesJob() throws InterruptedException {
        // Arrange
        AuditMutationEvent event = AuditMutationEvent.user(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.now(),
            List.of(AuditFieldChange.visible("firstName", "Old", "New"))
        );

        // Act
        this.transactions.executeWithoutResult(__ -> this.events.publish(event));
        boolean appended = awaitAudit(event.eventId(), Duration.ofSeconds(20));

        // Assert
        assertThat(appended).isTrue();
        assertThat(this.logs.list("user", 1, 100).items())
            .filteredOn(log -> log.sourceEventId().equals(event.eventId()))
            .hasSize(1);
    }

    private boolean awaitAudit(UUID eventId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            boolean found = this.logs
                .list("user", 1, 100)
                .items()
                .stream()
                .anyMatch(log -> log.sourceEventId().equals(eventId));
            if (found) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}

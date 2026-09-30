package io.taskmigo.worker.composition.audit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.TaskmigoWorkerApplication;
import io.taskmigo.audit.adapter.out.jobrunr.AuditJobRunrTransport;
import io.taskmigo.audit.application.port.in.query.AuditQueryService;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = { "jobrunr.background-job-server.enabled=false" })
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditWorkerIntegrationTest {

    private final JobScheduler jobs;
    private final AuditJobRunrTransport transport;
    private final AuditQueryService audits;
    private final JdbcTemplate jdbc;
    private final PostgreSQLContainer postgres;
    private final StorageProvider storage;

    AuditWorkerIntegrationTest(
        JobScheduler jobs,
        AuditJobRunrTransport transport,
        AuditQueryService audits,
        JdbcTemplate jdbc,
        PostgreSQLContainer postgres,
        StorageProvider storage
    ) {
        this.jobs = jobs;
        this.transport = transport;
        this.audits = audits;
        this.jdbc = jdbc;
        this.postgres = postgres;
        this.storage = storage;
    }

    /**
     * Verifies that JobRunr redelivery is effectively once at the audit persistence boundary.
     *
     * Given: the same stable audit event is enqueued, processed, and then enqueued again.
     * Expect: one immutable audit row remains and preserves the originating actor and field-level diff.
     */
    @Test
    @DisplayName("appends an externalized audit event and treats later redelivery as success")
    void shouldAppendOnceWhenJobRunrRedeliversAuditEvent() {
        // Arrange
        AuditEvent event = event();

        // Act
        this.enqueue(event);
        try (ConfigurableApplicationContext worker = this.workerContext()) {
            await()
                .atMost(15, SECONDS)
                .untilAsserted(() -> this.assertAppended(event));
        }

        this.enqueue(event);
        try (ConfigurableApplicationContext worker = this.workerContext()) {
            // Assert
            await()
                .atMost(15, SECONDS)
                .untilAsserted(() -> {
                    this.assertAppended(event);
                    assertThat(this.audits.list("user", 1, 100).items())
                        .filteredOn(log -> log.sourceEventId().equals(event.id()))
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.actor()).isEqualTo(event.actor());
                            assertThat(log.changes()).isEqualTo(event.changes());
                        });
                });
        }
    }

    /**
     * Verifies that a durable backlog survives a complete worker outage and drains when a worker application starts.
     *
     * Given: audit jobs persisted while no background server exists, followed by a new worker application context using the same PostgreSQL database.
     * Expect: every pending event is appended after the worker context starts and each source event is persisted exactly once.
     */
    @Test
    @DisplayName("drains durable backlog after a complete worker outage")
    void shouldDrainDurableBacklogWhenWorkerApplicationStarts() {
        // Arrange
        List<AuditEvent> events = List.of(event(), event(), event());
        events.forEach(this::enqueue);
        assertThat(events).allSatisfy(event -> assertThat(this.count(event.id())).isZero());

        // Act
        try (ConfigurableApplicationContext worker = this.workerContext()) {
            // Assert
            await()
                .atMost(20, SECONDS)
                .untilAsserted(() -> assertThat(events).allSatisfy(event -> this.assertAppended(event)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to verify worker recovery", exception);
        }
    }

    /**
     * Verifies that two worker application instances safely consume one shared durable backlog.
     *
     * Given: multiple distinct audit events persisted before two worker contexts start against the same PostgreSQL database.
     * Expect: the shared backlog drains completely and every source event is appended exactly once.
     */
    @Test
    @DisplayName("processes a shared backlog with multiple worker applications")
    void shouldProcessSharedBacklogWhenMultipleWorkersRun() {
        // Arrange
        List<AuditEvent> events = List.of(event(), event(), event(), event(), event(), event());
        events.forEach(this::enqueue);
        assertThat(events).allSatisfy(event -> assertThat(this.count(event.id())).isZero());

        // Act
        try (
            ConfigurableApplicationContext first = this.workerContext();
            ConfigurableApplicationContext second = this.workerContext()
        ) {
            // Assert
            await()
                .atMost(20, SECONDS)
                .untilAsserted(() -> assertThat(events).allSatisfy(event -> this.assertAppended(event)));
        }
    }

    /**
     * Verifies that pending work survives worker replacement during a rolling deployment.
     *
     * Given: one worker drains part of a durable backlog, shuts down, and a replacement worker starts on the same database.
     * Expect: all source events are eventually appended exactly once across the restart boundary.
     */
    @Test
    @DisplayName("preserves pending audit work across worker restart")
    void shouldPreservePendingAuditWorkWhenWorkerRestarts() {
        // Arrange
        List<AuditEvent> events = List.of(event(), event(), event(), event(), event(), event(), event(), event());
        events.forEach(this::enqueue);

        // Act
        try (ConfigurableApplicationContext first = this.workerContext()) {
            await()
                .atMost(20, SECONDS)
                .untilAsserted(() ->
                    assertThat(
                        events
                            .stream()
                            .mapToInt(event -> this.count(event.id()))
                            .sum()
                    ).isPositive()
                );
        }

        try (ConfigurableApplicationContext replacement = this.workerContext()) {
            // Assert
            await()
                .atMost(20, SECONDS)
                .untilAsserted(() -> assertThat(events).allSatisfy(event -> this.assertAppended(event)));
        }
    }

    /**
     * Verifies that a worker failure before audit commit leaves no row and succeeds on redelivery.
     *
     * Given: the audit table rejects the first append attempt before commit, then accepts a later redelivery of the same source event.
     * Expect: no audit row is committed for the failed attempt and exactly one row exists after the retry succeeds.
     */
    @Test
    @DisplayName("retries audit delivery when the worker fails before commit")
    void shouldRetryAuditDeliveryWhenWorkerFailsBeforeCommit() {
        // Arrange
        AuditEvent event = event();
        this.installAuditInsertFailure();

        try {
            // Act + Assert
            assertThatThrownBy(() -> this.transport.externalize(event)).isInstanceOf(RuntimeException.class);
            assertThat(this.count(event.id())).isZero();
        } finally {
            this.removeAuditInsertFailure();
        }

        // Act
        this.transport.externalize(event);

        // Assert
        this.assertAppended(event);
    }

    /**
     * Verifies that redelivery after a committed audit append behaves like a crash-before-ack recovery.
     *
     * Given: the source event has already been committed to the audit table but transport completion is replayed.
     * Expect: the second delivery completes successfully and the persistence uniqueness key keeps exactly one audit row.
     */
    @Test
    @DisplayName("deduplicates redelivery after the audit commit")
    void shouldDeduplicateAuditDeliveryWhenWorkerRedeliversAfterCommit() {
        // Arrange
        AuditEvent event = event();
        this.transport.externalize(event);
        this.assertAppended(event);

        // Act
        this.transport.externalize(event);

        // Assert
        this.assertAppended(event);
    }

    /**
     * Verifies that concurrent workers racing the same redelivery cannot create duplicates or fail the winning effect.
     *
     * Given: two worker threads invoke the production transport for the same stable source event at the same time.
     * Expect: both invocations complete successfully and PostgreSQL contains exactly one audit row for the source event.
     */
    @Test
    @DisplayName("atomically deduplicates concurrent worker delivery")
    void shouldAppendOnceWhenWorkersDeliverSameEventConcurrently() throws Exception {
        // Arrange
        AuditEvent event = event();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // Act
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> this.deliverAfterBarrier(event, ready, start));
            var second = executor.submit(() -> this.deliverAfterBarrier(event, ready, start));
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();
            first.get();
            second.get();
        }

        // Assert
        this.assertAppended(event);
    }

    /**
     * Verifies that permanently failing audit work remains observable instead of disappearing.
     *
     * Given: a poison payload that cannot be converted to an AuditEvent and a worker configured with the production retry policy.
     * Expect: JobRunr keeps the failed work persisted in its scheduled retry lifecycle instead of discarding it.
     */
    @Test
    @DisplayName("retains failed audit work for retry")
    void shouldRetainFailedAuditWorkWhenRetryIsScheduled() {
        // Arrange
        long scheduledBefore = this.storage.countJobs(StateName.SCHEDULED);
        this.jobs.enqueue(Stream.<JobRunrExternalizationTransport>of(this.transport), transport ->
            transport.externalize("poison-audit-event")
        );

        // Act
        try (ConfigurableApplicationContext worker = this.workerContext()) {
            // Assert
            await()
                .atMost(15, SECONDS)
                .untilAsserted(() ->
                    assertThat(this.storage.countJobs(StateName.SCHEDULED)).isGreaterThan(scheduledBefore)
                );
        }
    }

    /**
     * Verifies that one durable audit event can carry multiple field changes through the worker boundary.
     *
     * Given: one source event containing two non-sensitive field-level changes.
     * Expect: the worker appends exactly one audit row whose change list preserves both field diffs.
     */
    @Test
    @DisplayName("persists multiple field changes in one audit entry")
    void shouldPersistMultipleChangesWhenAuditEventContainsSeveralFields() {
        // Arrange
        AuditEvent event = new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.parse("2026-09-28T00:00:00Z"),
            List.of(AuditChange.visible("displayName", "Before", "After"), AuditChange.visible("enabled", true, false))
        );

        // Act
        this.transport.externalize(event);

        // Assert
        this.assertAppended(event);
        assertThat(this.audits.list("user", 1, 100).items())
            .filteredOn(log -> log.sourceEventId().equals(event.id()))
            .singleElement()
            .satisfies(log -> assertThat(log.changes()).containsExactlyElementsOf(event.changes()));
    }

    private void installAuditInsertFailure() {
        this.jdbc.execute(
            """
            create or replace function fail_audit_log_insert() returns trigger
            language plpgsql as 'begin raise exception ''forced audit append failure before commit''; end;'
            """
        );
        this.jdbc.execute(
            """
            create trigger fail_audit_log_insert
            before insert on audit_logs
            for each row execute function fail_audit_log_insert()
            """
        );
    }

    private void removeAuditInsertFailure() {
        this.jdbc.execute("drop trigger if exists fail_audit_log_insert on audit_logs");
        this.jdbc.execute("drop function if exists fail_audit_log_insert()");
    }

    private ConfigurableApplicationContext workerContext() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TaskmigoWorkerApplication.class)
            .web(WebApplicationType.NONE)
            .properties(
                "TASKMIGO_DATABASE_URL=" + this.postgres.getJdbcUrl(),
                "TASKMIGO_DATABASE_USERNAME=" + this.postgres.getUsername(),
                "TASKMIGO_DATABASE_PASSWORD=" + this.postgres.getPassword(),
                "jobrunr.database.skip-create=true",
                "jobrunr.background-job-server.enabled=true",
                "jobrunr.background-job-server.poll-interval-in-seconds=5",
                "taskmigo.audit.enabled=true"
            )
            .run();
        BackgroundJobServer server = context.getBean(BackgroundJobServer.class);
        server.start();
        return context;
    }

    private void deliverAfterBarrier(AuditEvent event, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            assertThat(start.await(5, SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating concurrent audit delivery", exception);
        }
        this.transport.externalize(event);
    }

    private int count(UUID sourceEventId) {
        Integer count = this.jdbc.queryForObject(
            "select count(*) from audit_logs where source_event_id = ?",
            Integer.class,
            sourceEventId
        );
        return count == null ? 0 : count;
    }

    private void enqueue(AuditEvent event) {
        this.jobs.enqueue(Stream.<JobRunrExternalizationTransport>of(this.transport), transport ->
            transport.externalize(event)
        );
    }

    private void assertAppended(AuditEvent event) {
        assertThat(this.count(event.id()))
            .as("JobRunr jobs for audit event %s: %s", event.id(), this.jobPayloads(event.id()))
            .isEqualTo(1);
    }

    private List<String> jobPayloads(UUID eventId) {
        return this.jdbc.query(
            "select jobAsJson from jobrunr_jobs where jobAsJson like ?",
            (resultSet, rowNumber) -> Objects.requireNonNull(resultSet.getString(1)),
            "%" + eventId + "%"
        );
    }

    private static AuditEvent event() {
        return new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.parse("2026-09-28T00:00:00Z"),
            List.of(AuditChange.visible("displayName", "Before", "After"))
        );
    }
}

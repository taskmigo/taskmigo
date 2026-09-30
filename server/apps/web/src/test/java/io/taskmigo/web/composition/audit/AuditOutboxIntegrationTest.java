package io.taskmigo.web.composition.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.TaskmigoApplication;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.storage.StorageProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    properties = {
        "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.oauth.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuditOutboxIntegrationTest {

    private final ApplicationEventPublisher events;
    private final PlatformTransactionManager transactions;
    private final StorageProvider jobs;
    private final JdbcTemplate jdbc;
    private final UserService users;
    private final PostgreSQLContainer postgres;

    AuditOutboxIntegrationTest(
        ApplicationEventPublisher events,
        PlatformTransactionManager transactions,
        StorageProvider jobs,
        JdbcTemplate jdbc,
        UserService users,
        PostgreSQLContainer postgres
    ) {
        this.events = events;
        this.transactions = transactions;
        this.jobs = jobs;
        this.jdbc = jdbc;
        this.users = users;
        this.postgres = postgres;
    }

    /**
     * Verifies that durable audit work follows the outcome of the originating business transaction.
     *
     * Given: one audit event published in a rolled-back transaction and another published in a committed transaction.
     * Expect: only the committed transaction adds an enqueued JobRunr outbox job.
     */
    @Test
    @DisplayName("enqueues audit work only when the originating transaction commits")
    void shouldEnqueueAuditWorkOnlyWhenOriginatingTransactionCommits() {
        // Arrange
        long before = this.jobs.countJobs(StateName.ENQUEUED);
        TransactionTemplate transaction = new TransactionTemplate(this.transactions);

        // Act
        transaction.executeWithoutResult(status -> {
            this.events.publishEvent(event());
            status.setRollbackOnly();
        });

        // Assert
        assertThat(this.jobs.countJobs(StateName.ENQUEUED)).isEqualTo(before);

        // Act
        transaction.executeWithoutResult(status -> this.events.publishEvent(event()));

        // Assert
        assertThat(this.jobs.countJobs(StateName.ENQUEUED)).isEqualTo(before + 1);
    }

    /**
     * Verifies that failure to persist durable audit work aborts the originating User mutation.
     *
     * Given: a real User statement mutation while PostgreSQL rejects every JobRunr job insert.
     * Expect: the mutation fails and its subject-statement binding is rolled back with the failed outbox insert.
     */
    @Test
    @DisplayName("rolls back the user mutation when durable audit publication cannot persist")
    void shouldRollBackUserMutationWhenDurableAuditPublicationFails() {
        // Arrange
        UUID userId = this.insertUser();
        UUID statementId = this.insertStatement();
        this.installJobInsertFailure();

        try {
            // Act + Assert
            assertThatThrownBy(() ->
                this.users.setStatements(
                    userId,
                    Set.of(statementId),
                    new UserMutationActor(UUID.randomUUID(), "operator")
                )
            ).isInstanceOf(RuntimeException.class);
            assertThat(
                this.jdbc.queryForObject(
                    "select count(*) from subject_statement_bindings where subject_type = 'identity:user' and subject_id = ?",
                    Integer.class,
                    userId
                )
            ).isZero();
        } finally {
            this.removeJobInsertFailure();
        }
    }

    /**
     * Verifies that a no-op User mutation does not create misleading durable audit work.
     *
     * Given: an existing User with no direct statements and a request to replace them with the same empty set.
     * Expect: no new JobRunr outbox job is enqueued.
     */
    @Test
    @DisplayName("does not publish audit work for a no-op user mutation")
    void shouldNotPublishAuditWorkWhenUserMutationIsNoOp() {
        // Arrange
        UUID userId = this.insertUser();
        long before = this.jobs.countJobs(StateName.ENQUEUED);

        // Act
        this.users.setStatements(userId, Set.of(), new UserMutationActor(UUID.randomUUID(), "operator"));

        // Assert
        assertThat(this.jobs.countJobs(StateName.ENQUEUED)).isEqualTo(before);
    }

    /**
     * Verifies that concurrent web application instances can publish to one shared durable outbox without losing work.
     *
     * Given: two independent web application contexts connected to the same PostgreSQL database and multiple concurrent audit publications.
     * Expect: every publication is durably stored once even though producer instances operate concurrently.
     */
    @Test
    @DisplayName("persists concurrent audit publications from multiple web applications")
    void shouldPersistAuditWorkWhenMultipleWebApplicationsPublishConcurrently() throws Exception {
        // Arrange
        List<AuditEvent> events = List.of(event(), event(), event(), event(), event(), event());

        try (
            ConfigurableApplicationContext first = this.webContext();
            ConfigurableApplicationContext second = this.webContext();
            var executor = Executors.newVirtualThreadPerTaskExecutor()
        ) {
            ApplicationEventPublisher firstPublisher = first;
            ApplicationEventPublisher secondPublisher = second;

            // Act
            List<Future<?>> publications = new ArrayList<>();
            for (int index = 0; index < events.size(); index++) {
                boolean firstProducer = index % 2 == 0;
                ApplicationEventPublisher publisher = firstProducer ? firstPublisher : secondPublisher;
                ConfigurableApplicationContext context = firstProducer ? first : second;
                AuditEvent event = events.get(index);
                publications.add(executor.submit(() -> publishInTransaction(publisher, event, context)));
            }
            for (Future<?> publication : publications) {
                publication.get();
            }
        }

        // Assert
        assertThat(events).allSatisfy(event -> assertThat(this.countDurableJobs(event.id())).isEqualTo(1));
    }

    /**
     * Verifies that sensitive values are absent before an audit event reaches durable outbox persistence.
     *
     * Given: a committed audit event whose passwordHash change is represented by a sensitive marker.
     * Expect: the JobRunr payload identifies the sensitive field but contains neither the previous nor the new secret.
     */
    @Test
    @DisplayName("omits sensitive values from the durable outbox payload")
    void shouldOmitSensitiveValuesWhenAuditEventIsDurablyPublished() {
        // Arrange
        String oldSecret = "{bcrypt}old-secret";
        String newSecret = "{bcrypt}new-secret";
        AuditEvent event = new AuditEvent(
            UUID.randomUUID(),
            "user",
            UUID.randomUUID(),
            new AuditActor(UUID.randomUUID(), "operator"),
            Instant.parse("2026-09-28T00:00:00Z"),
            List.of(AuditChange.sensitive("passwordHash"))
        );
        TransactionTemplate transaction = new TransactionTemplate(this.transactions);

        // Act
        transaction.executeWithoutResult(status -> this.events.publishEvent(event));

        // Assert
        List<String> payloads = this.jdbc.query(
            "select jobAsJson from jobrunr_jobs where jobAsJson like ?",
            (resultSet, rowNumber) -> Objects.requireNonNull(resultSet.getString(1)),
            "%" + event.id() + "%"
        );
        assertThat(payloads)
            .singleElement()
            .satisfies(payload -> {
                assertThat(payload).contains("passwordHash");
                assertThat(payload).doesNotContain(oldSecret, newSecret);
            });
    }

    private ConfigurableApplicationContext webContext() {
        return new SpringApplicationBuilder(TaskmigoApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties(
                "TASKMIGO_DATABASE_URL=" + this.postgres.getJdbcUrl(),
                "TASKMIGO_DATABASE_USERNAME=" + this.postgres.getUsername(),
                "TASKMIGO_DATABASE_PASSWORD=" + this.postgres.getPassword(),
                "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
                "TASKMIGO_OAUTH_SIGNING_KEY_AUTO_CREATE=true",
                "server.port=0",
                "jobrunr.database.skip-create=true",
                "jobrunr.background-job-server.enabled=false",
                "taskmigo.audit.enabled=true"
            )
            .run();
    }

    private static void publishInTransaction(
        ApplicationEventPublisher publisher,
        AuditEvent event,
        ConfigurableApplicationContext context
    ) {
        PlatformTransactionManager manager = context.getBean(PlatformTransactionManager.class);
        new TransactionTemplate(manager).executeWithoutResult(status -> publisher.publishEvent(event));
    }

    private int countDurableJobs(UUID eventId) {
        return this.jdbc
            .queryForList(
                "select jobAsJson from jobrunr_jobs where jobAsJson like ?",
                String.class,
                "%" + eventId + "%"
            )
            .size();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "insert into users (id, username, first_name, last_name, status) values (?, ?, 'Audit', 'Test', 'ACTIVE')",
            id,
            "audit-" + id
        );
        return id;
    }

    private UUID insertStatement() {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            """
            insert into statements (id, code, effect, scope, method, path, policy)
            values (?, ?, 'ALLOW', 'REQUEST', 'GET', '/api/v0/users', 'return true;')
            """,
            id,
            "audit_" + id.toString().replace("-", "")
        );
        return id;
    }

    private void installJobInsertFailure() {
        this.jdbc.execute(
            """
            create or replace function fail_audit_job_insert() returns trigger as $$
            begin
                raise exception 'forced audit outbox persistence failure';
            end;
            $$ language plpgsql
            """
        );
        this.jdbc.execute(
            """
            create trigger fail_audit_job_insert
            before insert on jobrunr_jobs
            for each row execute function fail_audit_job_insert()
            """
        );
    }

    private void removeJobInsertFailure() {
        this.jdbc.execute("drop trigger if exists fail_audit_job_insert on jobrunr_jobs");
        this.jdbc.execute("drop function if exists fail_audit_job_insert()");
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

package io.taskmigo.web.composition.audit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(
    properties = {
        "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.oauth.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@NullMarked
class AuditTransactionIntegrationTest {

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final IdentityProvisioningService provisioning;
    private final PlatformTransactionManager transactions;

    AuditTransactionIntegrationTest(
        JdbcTemplate jdbc,
        UserService users,
        IdentityProvisioningService provisioning,
        PlatformTransactionManager transactions
    ) {
        this.jdbc = jdbc;
        this.users = users;
        this.provisioning = provisioning;
        this.transactions = transactions;
    }

    /**
     * Verifies that a User mutation and its audit record are committed synchronously as one operation.
     *
     * Given: an existing User whose direct Statement assignment changes.
     * Expect: the assignment and final audit row both exist when the service call returns.
     */
    @Test
    @DisplayName("commits user mutation and audit row synchronously")
    void shouldCommitAuditWithUserMutationWhenAuditPersistenceSucceeds() {
        // Arrange
        UUID userId = this.insertUser();
        UUID statementId = this.insertStatement();
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "operator");

        // Act
        this.users.setStatements(userId, Set.of(statementId), actor);

        // Assert
        assertThat(this.bindingCount(userId)).isOne();
        assertThat(
            this.jdbc.queryForList(
                """
                select actor_id, actor_username, occurred_at, changes_json
                from audit_logs
                where entity_type = 'user' and entity_id = ?
                """,
                userId
            )
        )
            .singleElement()
            .satisfies(row -> {
                assertThat(row.get("actor_id")).isEqualTo(actor.id());
                assertThat(row.get("actor_username")).isEqualTo(actor.username());
                Timestamp occurredAt = (Timestamp) Objects.requireNonNull(row.get("occurred_at"));
                assertThat(occurredAt.toInstant().getNano() % 1_000_000).isZero();
                assertThat(Objects.requireNonNull(row.get("changes_json")).toString()).contains(
                    "statementIds",
                    statementId.toString()
                );
            });
    }

    /**
     * Verifies that an audit persistence failure aborts the owning User mutation.
     *
     * Given: PostgreSQL rejects insertion into audit_logs during a real Statement mutation.
     * Expect: the service fails and the Statement assignment is rolled back with the audit write.
     */
    @Test
    @DisplayName("rolls back user mutation when synchronous audit persistence fails")
    void shouldRollBackUserMutationWhenAuditPersistenceFails() {
        // Arrange
        UUID userId = this.insertUser();
        UUID statementId = this.insertStatement();
        this.installAuditInsertFailure();

        try {
            // Act + Assert
            assertThatThrownBy(() ->
                this.users.setStatements(
                    userId,
                    Set.of(statementId),
                    new UserMutationActor(UUID.randomUUID(), "operator")
                )
            ).isInstanceOf(RuntimeException.class);
            assertThat(this.bindingCount(userId)).isZero();
            assertThat(this.auditCount(userId)).isZero();
        } finally {
            this.removeAuditInsertFailure();
        }
    }

    /**
     * Verifies that sensitive credential values never reach persisted audit data.
     *
     * Given: managed reconciliation initializes a missing User credential with a secret hash.
     * Expect: the synchronous audit row marks passwordHash as sensitive without persisting the hash value.
     */
    @Test
    @DisplayName("redacts sensitive values from synchronous audit persistence")
    void shouldRedactSensitiveValueWhenManagedCredentialChanges() {
        // Arrange
        UUID systemId = this.users
            .findForAuthentication(SystemUser.USERNAME)
            .map(info -> info.id())
            .orElseGet(() -> this.insertUser(SystemUser.USERNAME));
        String username = "sensitive-" + UUID.randomUUID();
        UUID userId = this.insertUser(username);
        String secretHash = "{bcrypt}sensitive-test-hash";

        // Act
        this.provisioning.reconcileUser(username, secretHash, Set.of(), "Audit", "Test", Set.of(), Set.of());

        // Assert
        assertThat(
            this.jdbc.queryForList(
                "select actor_id, changes_json from audit_logs where entity_type = 'user' and entity_id = ?",
                userId
            )
        )
            .singleElement()
            .satisfies(row -> {
                assertThat(row.get("actor_id")).isEqualTo(systemId);
                assertThat(Objects.requireNonNull(row.get("changes_json")).toString())
                    .contains("passwordHash")
                    .doesNotContain(secretHash);
            });
    }

    /**
     * Verifies that an unchanged User operation creates no misleading audit record.
     *
     * Given: an existing User with no direct Statements and a request for the same empty set.
     * Expect: the service returns successfully without appending an audit row.
     */
    @Test
    @DisplayName("does not append audit for a no-op user mutation")
    void shouldNotAppendAuditWhenUserMutationIsNoOp() {
        // Arrange
        UUID userId = this.insertUser();

        // Act
        this.users.setStatements(userId, Set.of(), new UserMutationActor(UUID.randomUUID(), "operator"));

        // Assert
        assertThat(this.auditCount(userId)).isZero();
    }

    /**
     * Verifies that mutation locking is scoped to one User instead of serializing all Users.
     *
     * Given: User A is held by a PostgreSQL row lock while User A and User B mutations are submitted concurrently.
     * Expect: User A waits for its row lock, User B completes independently, then User A completes after release.
     */
    @Test
    @DisplayName("serializes mutations per user row without globally blocking other users")
    void shouldSerializeOnlySameUserWhenUserRowIsLocked() throws Exception {
        // Arrange
        UUID firstUser = this.insertUser();
        UUID secondUser = this.insertUser();
        UUID firstStatement = this.insertStatement();
        UUID secondStatement = this.insertStatement();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> lockHolder = executor.submit(() -> this.holdUserLock(firstUser, locked, release));
            assertThat(locked.await(5, SECONDS)).isTrue();

            Future<?> blockedMutation = executor.submit(() ->
                this.users.setStatements(
                    firstUser,
                    Set.of(firstStatement),
                    new UserMutationActor(UUID.randomUUID(), "operator-a")
                )
            );
            Future<?> independentMutation = executor.submit(() ->
                this.users.setStatements(
                    secondUser,
                    Set.of(secondStatement),
                    new UserMutationActor(UUID.randomUUID(), "operator-b")
                )
            );

            // Act + Assert
            independentMutation.get(5, SECONDS);
            assertThatThrownBy(() -> blockedMutation.get(250, MILLISECONDS)).isInstanceOf(TimeoutException.class);
            assertThat(this.bindingCount(firstUser)).isZero();
            assertThat(this.bindingCount(secondUser)).isOne();

            release.countDown();
            lockHolder.get(5, SECONDS);
            blockedMutation.get(5, SECONDS);
        }

        assertThat(this.bindingCount(firstUser)).isOne();
        assertThat(this.auditCount(firstUser)).isOne();
        assertThat(this.auditCount(secondUser)).isOne();
    }

    private void holdUserLock(UUID userId, CountDownLatch locked, CountDownLatch release) {
        new TransactionTemplate(this.transactions).executeWithoutResult(status -> {
            this.jdbc.queryForObject("select id from users where id = ? for update", UUID.class, userId);
            locked.countDown();
            try {
                assertThat(release.await(5, SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while holding User row lock", exception);
            }
        });
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        return this.insertUser("audit-" + id, id);
    }

    private UUID insertUser(String username) {
        return this.insertUser(username, UUID.randomUUID());
    }

    private UUID insertUser(String username, UUID id) {
        this.jdbc.update(
            "insert into users (id, username, first_name, last_name, status) values (?, ?, 'Audit', 'Test', 'ACTIVE')",
            id,
            username
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

    private int bindingCount(UUID userId) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject(
                "select count(*) from subject_statement_bindings where subject_type = 'identity:user' and subject_id = ?",
                Integer.class,
                userId
            )
        );
    }

    private int auditCount(UUID userId) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject(
                "select count(*) from audit_logs where entity_type = 'user' and entity_id = ?",
                Integer.class,
                userId
            )
        );
    }

    private void installAuditInsertFailure() {
        this.jdbc.execute(
            """
            create or replace function fail_audit_log_insert() returns trigger as $$
            begin
                raise exception 'forced synchronous audit persistence failure';
            end;
            $$ language plpgsql
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
}

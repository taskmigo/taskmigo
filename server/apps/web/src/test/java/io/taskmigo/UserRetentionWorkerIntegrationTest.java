package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    properties = {
        "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.oauth.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserRetentionWorkerIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-03T00:00:00Z");

    private final JdbcTemplate jdbc;
    private final UserRetentionService retention;
    private final List<UUID> users = new ArrayList<>();

    UserRetentionWorkerIntegrationTest(JdbcTemplate jdbc, UserRetentionService retention) {
        this.jdbc = jdbc;
        this.retention = retention;
    }

    @BeforeEach
    void resetRetention() {
        this.setRetention("P30D");
    }

    @AfterEach
    void cleanCreatedUsers() {
        for (UUID userId : this.users) {
            this.jdbc.update("delete from audit_logs where entity_type = 'user' and entity_id = ?", userId);
            this.jdbc.update(
                "delete from subject_role_bindings where subject_type = 'identity:user' and subject_id = ?",
                userId
            );
            this.jdbc.update(
                "delete from subject_statement_bindings where subject_type = 'identity:user' and subject_id = ?",
                userId
            );
            this.jdbc.update("delete from group_members where user_id = ?", userId);
            this.jdbc.update("delete from users where id = ?", userId);
        }
        this.removeAuditFailure();
    }

    @Test
    @DisplayName("tombstones expired retained users but not unexpired users")
    void shouldTombstoneOnlyExpiredRetainedUsers() {
        UUID expired = this.insertRetained(NOW.minusSeconds(31L * 24 * 60 * 60));
        UUID unexpired = this.insertRetained(NOW.minusSeconds(5L * 24 * 60 * 60));

        int count = this.retention.purgeExpiredUsers(NOW);

        assertThat(count).isEqualTo(1);
        assertThat(this.status(expired)).isEqualTo(UserStatus.TOMBSTONE);
        assertThat(this.status(unexpired)).isEqualTo(UserStatus.RETAINED);
        assertThat(this.tombstonedAt(expired)).isEqualTo(NOW);
        assertThat(this.retainedAt(unexpired)).isEqualTo(NOW.minusSeconds(5L * 24 * 60 * 60));
    }

    @Test
    @DisplayName("uses current retention configuration without rewriting retainedAt")
    void shouldUseCurrentRetentionWithoutChangingOriginalTimestamp() {
        Instant originalRetainedAt = NOW.minusSeconds(5L * 24 * 60 * 60);
        UUID userId = this.insertRetained(originalRetainedAt);

        assertThat(this.retention.purgeExpiredUsers(NOW)).isZero();
        this.setRetention("P1D");

        assertThat(this.retention.purgeExpiredUsers(NOW)).isEqualTo(1);
        assertThat(this.status(userId)).isEqualTo(UserStatus.TOMBSTONE);
        assertThat(this.retainedAt(userId)).isEqualTo(originalRetainedAt);
    }

    @Test
    @DisplayName("multiple workers tombstone one retained user exactly once")
    void shouldClaimExpiredUserOnceAcrossConcurrentWorkers() throws Exception {
        UUID userId = this.insertRetained(NOW.minusSeconds(31L * 24 * 60 * 60));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Integer> first = executor.submit(() -> this.retention.purgeExpiredUsers(NOW));
            Future<Integer> second = executor.submit(() -> this.retention.purgeExpiredUsers(NOW));

            assertThat(first.get() + second.get()).isEqualTo(1);
        }

        assertThat(this.status(userId)).isEqualTo(UserStatus.TOMBSTONE);
        assertThat(
            this.jdbc.queryForObject(
                """
                select count(*)
                from audit_logs
                where entity_type = 'user'
                  and entity_id = ?
                  and changes_json like '%TOMBSTONE%'
                """,
                Integer.class,
                userId
            )
        ).isOne();
        assertThat(
            this.jdbc.queryForObject(
                """
                select actor_username
                from audit_logs
                where entity_type = 'user'
                  and entity_id = ?
                  and changes_json like '%TOMBSTONE%'
                limit 1
                """,
                String.class,
                userId
            )
        ).isEqualTo("system");
    }

    @Test
    @DisplayName("continues after one tombstone fails and retries it on a later run")
    void shouldContinueAndRetryWhenOneTombstoneFails() {
        UUID failing = this.insertRetained(NOW.minusSeconds(40L * 24 * 60 * 60));
        UUID healthy = this.insertRetained(NOW.minusSeconds(35L * 24 * 60 * 60));
        this.installAuditFailure(failing);

        int firstRun = this.retention.purgeExpiredUsers(NOW);

        assertThat(firstRun).isEqualTo(1);
        assertThat(this.status(failing)).isEqualTo(UserStatus.RETAINED);
        assertThat(this.status(healthy)).isEqualTo(UserStatus.TOMBSTONE);

        this.removeAuditFailure();

        int secondRun = this.retention.purgeExpiredUsers(NOW);

        assertThat(secondRun).isEqualTo(1);
        assertThat(this.status(failing)).isEqualTo(UserStatus.TOMBSTONE);
    }

    private UUID insertRetained(Instant retainedAt) {
        UUID id = UUID.randomUUID();
        this.users.add(id);
        this.jdbc.update(
            """
            insert into users (id, username, first_name, last_name, status, retained_at)
            values (?, ?, 'Worker', 'Test', 'RETAINED', ?)
            """,
            id,
            "worker-test-" + id,
            retainedAt
        );
        return id;
    }

    private void setRetention(String duration) {
        this.jdbc.update(
            """
            insert into application_configuration (configuration_key, configuration_value)
            values ('retention.user', ?)
            on conflict (configuration_key)
            do update set configuration_value = excluded.configuration_value
            """,
            duration
        );
    }

    private UserStatus status(UUID userId) {
        return UserStatus.valueOf(
            Objects.requireNonNull(
                this.jdbc.queryForObject("select status from users where id = ?", String.class, userId)
            )
        );
    }

    private Instant retainedAt(UUID userId) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject("select retained_at from users where id = ?", Timestamp.class, userId)
        ).toInstant();
    }

    private Instant tombstonedAt(UUID userId) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject("select tombstoned_at from users where id = ?", Timestamp.class, userId)
        ).toInstant();
    }

    private void installAuditFailure(UUID userId) {
        this.jdbc.execute(
            """
            create or replace function fail_selected_tombstone_audit() returns trigger as $$
            begin
                if NEW.entity_id = '%s'::uuid and NEW.changes_json like '%%TOMBSTONE%%' then
                    raise exception 'forced tombstone audit failure';
                end if;
                return NEW;
            end;
            $$ language plpgsql
            """.formatted(userId)
        );
        this.jdbc.execute(
            """
            create trigger fail_selected_tombstone_audit
            before insert on audit_logs
            for each row execute function fail_selected_tombstone_audit()
            """
        );
    }

    private void removeAuditFailure() {
        this.jdbc.execute("drop trigger if exists fail_selected_tombstone_audit on audit_logs");
        this.jdbc.execute("drop function if exists fail_selected_tombstone_audit()");
    }
}

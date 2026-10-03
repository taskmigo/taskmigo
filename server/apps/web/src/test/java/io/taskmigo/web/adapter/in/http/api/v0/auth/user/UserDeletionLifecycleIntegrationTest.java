package io.taskmigo.web.adapter.in.http.api.v0.auth.user;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateGroupRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateRoleRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateStatementRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.CreateUserRequest;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementApiTarget;
import io.taskmigo.web.adapter.in.http.api.v0.testing.TaskmigoApiClient.StatementTarget;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class UserDeletionLifecycleIntegrationTest extends ApiIntegrationTestSupport {

    private final JdbcTemplate jdbc;
    private final IdentityProvisioningService provisioning;

    UserDeletionLifecycleIntegrationTest(JdbcTemplate jdbc, IdentityProvisioningService provisioning) {
        this.jdbc = jdbc;
        this.provisioning = provisioning;
    }

    @BeforeEach
    void resetRetention() {
        this.jdbc.update(
            """
            insert into application_configuration (configuration_key, configuration_value)
            values ('retention.user', 'P30D')
            on conflict (configuration_key)
            do update set configuration_value = excluded.configuration_value
            """
        );
    }

    /**
     * Given: an active User with direct access plus OAuth authorization/session state.
     * Expect: non-zero retention preserves PII but immediately removes active access/session state.
     */
    @Test
    @DisplayName("retains PII while immediately revoking access for non-zero retention")
    void shouldRetainUserAndRevokeAccessWhenRetentionIsNonZero() {
        // Arrange
        ManagedUser managed = this.createManagedUser("retained");
        this.insertOAuthState(managed.username());

        // Act
        this.api().users().delete(managed.id());

        // Assert
        Map<String, Object> user = this.userRow(managed.id());
        assertThat(user.get("status")).isEqualTo("RETAINED");
        assertThat(user.get("username")).isEqualTo(managed.username());
        assertThat(user.get("first_name")).isEqualTo("Lifecycle");
        assertThat(user.get("last_name")).isEqualTo("User");
        assertThat(user.get("password_hash")).isEqualTo("{bcrypt}retention-test");
        assertThat(user.get("retained_at")).isNotNull();
        assertThat(user.get("tombstoned_at")).isNull();

        assertThat(this.emailCount(managed.id())).isOne();
        assertThat(this.groupCount(managed.id())).isZero();
        assertThat(this.roleCount(managed.id())).isZero();
        assertThat(this.statementCount(managed.id())).isZero();
        assertThat(this.oauthAuthorizationCount(managed.username())).isZero();
        assertThat(this.oauthConsentCount(managed.username())).isZero();

        String deletionAudit = this.latestAuditChanges(managed.id());
        assertThat(deletionAudit)
            .contains("\"field\":\"roleIds\"", "\"field\":\"statementIds\"", "\"field\":\"groupIds\"")
            .contains("\"sensitive\":true")
            .doesNotContain(
                managed.roleId().toString(),
                managed.statementId().toString(),
                managed.groupId().toString()
            );
    }

    /**
     * Given: zero retention and a User carrying every purgeable identity/access category plus historical audit PII.
     * Expect: DELETE synchronously leaves only the stable tombstone identity/lifecycle data and releases unique values.
     */
    @Test
    @DisplayName("tombstones synchronously and scrubs PII when retention is zero")
    void shouldTombstoneSynchronouslyWhenRetentionIsZero() {
        // Arrange
        this.api().patchJson("/api/v0/configuration", "{\"retention\":{\"user\":\"P0D\"}}");
        ManagedUser managed = this.createManagedUser("tombstone");
        this.insertOAuthState(managed.username());
        UUID historicalAuditId = this.insertHistoricalPiiAudit(managed);

        // Act
        this.api().users().delete(managed.id());

        // Assert
        Map<String, Object> user = this.userRow(managed.id());
        assertThat(user.get("id")).isEqualTo(managed.id());
        assertThat(user.get("status")).isEqualTo("TOMBSTONE");
        assertThat(user.get("username")).isNull();
        assertThat(user.get("first_name")).isNull();
        assertThat(user.get("last_name")).isNull();
        assertThat(user.get("password_hash")).isNull();
        assertThat(user.get("retained_at")).isNull();
        assertThat(user.get("tombstoned_at")).isNotNull();

        assertThat(this.emailCount(managed.id())).isZero();
        assertThat(this.groupCount(managed.id())).isZero();
        assertThat(this.roleCount(managed.id())).isZero();
        assertThat(this.statementCount(managed.id())).isZero();
        assertThat(this.oauthAuthorizationCount(managed.username())).isZero();
        assertThat(this.oauthConsentCount(managed.username())).isZero();

        String usersResponse = this.api().get("/api/v0/users?page=1&pageSize=100");
        assertThat(usersResponse).doesNotContain(managed.id().toString());

        UUID replacement = this.api()
            .users()
            .create(
                new CreateUserRequest(
                    managed.username(),
                    Set.of(managed.email()),
                    "Replacement",
                    "User",
                    Set.of(),
                    Set.of()
                )
            );
        assertThat(replacement).isNotEqualTo(managed.id());

        Map<String, Object> historicalAudit = this.jdbc.queryForMap(
            "select actor_id, actor_username, changes_json from audit_logs where id = ?",
            historicalAuditId
        );
        assertThat(historicalAudit.get("actor_id")).isEqualTo(managed.id());
        assertThat(historicalAudit.get("actor_username")).isEqualTo("Unknown user");
        assertThat(historicalAudit.get("changes_json").toString())
            .doesNotContain("Lifecycle", managed.email(), managed.username())
            .contains("\"sensitive\":true");

        String tombstoneAudit = this.jdbc.queryForObject(
            """
            select changes_json
            from audit_logs
            where entity_type = 'user'
              and entity_id = ?
              and changes_json like '%TOMBSTONE%'
            order by occurred_at desc, id desc
            limit 1
            """,
            String.class,
            managed.id()
        );
        assertThat(tombstoneAudit)
            .contains(
                "\"field\":\"username\"",
                "\"field\":\"firstName\"",
                "\"field\":\"lastName\"",
                "\"field\":\"emails\"",
                "\"field\":\"passwordHash\"",
                "\"field\":\"roleIds\"",
                "\"field\":\"statementIds\"",
                "\"field\":\"groupIds\"",
                "\"field\":\"status\"",
                "TOMBSTONE",
                "\"field\":\"tombstonedAt\""
            )
            .doesNotContain(managed.username(), managed.email(), "{bcrypt}retention-test");

        assertThat(this.api().get("/api/v0/audit/user/logs?page=1&pageSize=100")).contains("Unknown user");
    }

    private ManagedUser createManagedUser(String prefix) {
        UUID roleId = this.api()
            .roles()
            .create(new CreateRoleRequest("retention-role-" + UUID.randomUUID(), null, Set.of()));
        UUID groupId = this.api()
            .groups()
            .create(new CreateGroupRequest("retention-group-" + UUID.randomUUID(), null, Set.of(), Set.of()));
        UUID statementId = this.api()
            .statements()
            .create(
                new CreateStatementRequest(
                    "retention-statement-" + UUID.randomUUID(),
                    null,
                    "allow",
                    "request",
                    new StatementTarget(new StatementApiTarget("GET", "/api/v0/users")),
                    "return true;"
                )
            );
        String username = prefix + "-" + UUID.randomUUID();
        String email = username + "@example.com";
        UUID userId = this.provisioning
            .reconcileUser(
                username,
                "{bcrypt}retention-test",
                Set.of(email),
                "Lifecycle",
                "User",
                Set.of(roleId),
                Set.of(groupId)
            )
            .id();
        this.api().users().replaceStatements(userId, Set.of(statementId));
        return new ManagedUser(userId, username, email, roleId, groupId, statementId);
    }

    private void insertOAuthState(String username) {
        this.jdbc.update(
            """
            insert into oauth2_authorization (
                id, registered_client_id, principal_name, authorization_grant_type
            ) values (?, 'browser', ?, 'authorization_code')
            """,
            UUID.randomUUID().toString(),
            username
        );
        this.jdbc.update(
            """
            insert into oauth2_authorization_consent (
                registered_client_id, principal_name, authorities
            ) values ('browser', ?, 'openid')
            """,
            username
        );
    }

    private UUID insertHistoricalPiiAudit(ManagedUser user) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            """
            insert into audit_logs (
                id, entity_type, entity_id, actor_id, actor_username, occurred_at, changes_json
            ) values (?, 'user', ?, ?, ?, ?, ?)
            """,
            id,
            user.id(),
            user.id(),
            user.username(),
            Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")),
            """
            [
              {"field":"username","before":"old","after":"%s","sensitive":false},
              {"field":"firstName","before":"Old","after":"Lifecycle","sensitive":false},
              {"field":"emails","before":[],"after":["%s"],"sensitive":false}
            ]
            """.formatted(user.username(), user.email())
        );
        return id;
    }

    private Map<String, Object> userRow(UUID userId) {
        return this.jdbc.queryForMap(
            """
            select id, username, first_name, last_name, status, retained_at, tombstoned_at, password_hash
            from users
            where id = ?
            """,
            userId
        );
    }

    private int emailCount(UUID userId) {
        return this.count("select count(*) from user_emails where user_id = ?", userId);
    }

    private int groupCount(UUID userId) {
        return this.count("select count(*) from group_members where user_id = ?", userId);
    }

    private int roleCount(UUID userId) {
        return this.count(
            "select count(*) from subject_role_bindings where subject_type = 'identity:user' and subject_id = ?",
            userId
        );
    }

    private int statementCount(UUID userId) {
        return this.count(
            "select count(*) from subject_statement_bindings where subject_type = 'identity:user' and subject_id = ?",
            userId
        );
    }

    private int oauthAuthorizationCount(String username) {
        return this.count("select count(*) from oauth2_authorization where principal_name = ?", username);
    }

    private int oauthConsentCount(String username) {
        return this.count("select count(*) from oauth2_authorization_consent where principal_name = ?", username);
    }

    private int count(String sql, Object value) {
        return this.jdbc.queryForObject(sql, Integer.class, value);
    }

    private String latestAuditChanges(UUID userId) {
        return this.jdbc.queryForObject(
            """
            select changes_json
            from audit_logs
            where entity_type = 'user' and entity_id = ?
            order by occurred_at desc, id desc
            limit 1
            """,
            String.class,
            userId
        );
    }

    private record ManagedUser(
        UUID id,
        String username,
        String email,
        UUID roleId,
        UUID groupId,
        UUID statementId
    ) {}
}

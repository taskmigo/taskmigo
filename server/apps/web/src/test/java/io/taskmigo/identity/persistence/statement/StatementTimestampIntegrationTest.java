package io.taskmigo.identity.persistence.statement;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.identity.authorization.statement.StatementService;
import io.taskmigo.identity.user.UserService;
import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementTimestampIntegrationTest extends ApiIntegrationTestSupport {

    private final StatementService statements;
    private final StatementRepository statementRepository;
    private final UserService users;
    private final EffectiveStatementResolver resolver;

    StatementTimestampIntegrationTest(
        StatementService statements,
        StatementRepository statementRepository,
        UserService users,
        EffectiveStatementResolver resolver
    ) {
        this.statements = statements;
        this.statementRepository = statementRepository;
        this.users = users;
        this.resolver = resolver;
    }

    /**
     * Verifies that Statement timestamps are database-owned and `updated_at` is the revision exposed to Authorization.
     *
     * Given: a persisted Statement assigned directly to a User and then reconciled with changed persisted state.
     * Expect: `created_at` remains stable, `updated_at` advances, and effective resolution exposes each DB value.
     */
    @Test
    @DisplayName("advances statement updated_at and exposes it as the authorization revision")
    void shouldAdvanceUpdatedAtWhenStatementIsUpdated() {
        // Arrange
        String suffix = UUID.randomUUID().toString();
        String statementName = "timestamp-" + suffix;
        UUID statementId = this.statements.create(
            statementName,
            "before",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/timestamp",
            "return true;"
        );
        UUID userId = this.users.create(
            "timestamp-user-" + suffix,
            Set.of("timestamp-" + suffix + "@example.com"),
            "Timestamp",
            "User",
            List.of()
        );
        this.users.setStatements(userId, List.of(statementId));
        StatementEntity created = this.statementRepository.findById(statementId).orElseThrow();
        Instant createdAt = created.createdAt();
        Instant initialUpdatedAt = created.updatedAt();
        EffectiveStatement initialResolved = this.resolver.resolve(userId).getFirst();

        // Act
        this.statements.reconcile(
            statementName,
            "after",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/timestamp",
            "return true;"
        );
        StatementEntity updated = this.statementRepository.findById(statementId).orElseThrow();
        EffectiveStatement updatedResolved = this.resolver.resolve(userId).getFirst();

        // Assert
        assertThat(initialUpdatedAt).isAfterOrEqualTo(createdAt);
        assertThat(initialResolved.updatedAt()).isEqualTo(initialUpdatedAt);
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isAfter(initialUpdatedAt);
        assertThat(updatedResolved.statement().id()).isEqualTo(statementId);
        assertThat(updatedResolved.updatedAt()).isEqualTo(updated.updatedAt());
    }
}

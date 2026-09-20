package io.taskmigo.authorization.statement.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.identity.user.UserRegistrationService;
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
    private final AuthorizationProvisioningService provisioning;
    private final StatementRepository statementRepository;
    private final UserRegistrationService registrations;
    private final UserService users;
    private final EffectiveStatementResolver resolver;

    StatementTimestampIntegrationTest(
        StatementService statements,
        AuthorizationProvisioningService provisioning,
        StatementRepository statementRepository,
        UserRegistrationService registrations,
        UserService users,
        EffectiveStatementResolver resolver
    ) {
        this.statements = statements;
        this.provisioning = provisioning;
        this.statementRepository = statementRepository;
        this.registrations = registrations;
        this.users = users;
        this.resolver = resolver;
    }

    @Test
    @DisplayName("advances statement updated_at and exposes it as the authorization revision")
    void shouldAdvanceUpdatedAtWhenStatementIsUpdated() {
        String suffix = UUID.randomUUID().toString();
        String statementCode = "timestamp-" + suffix;
        UUID statementId = this.statements.create(
            statementCode,
            "before",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/timestamp",
            "return true;"
        );
        UUID userId = this.registrations.register(
            "timestamp-user-" + suffix,
            Set.of("timestamp-" + suffix + "@example.com"),
            "Timestamp",
            "User",
            Set.of(),
            Set.of()
        );
        this.users.setStatements(userId, List.of(statementId));
        StatementEntity created = this.statementRepository.findById(statementId).orElseThrow();
        Instant createdAt = created.createdAt();
        Instant initialUpdatedAt = created.updatedAt();
        EffectiveStatement initialResolved = this.resolver.resolve(userId).getFirst();

        this.provisioning.reconcileStatement(
            statementCode,
            "after",
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/timestamp",
            "return true;"
        );
        StatementEntity updated = this.statementRepository.findById(statementId).orElseThrow();
        EffectiveStatement updatedResolved = this.resolver.resolve(userId).getFirst();

        assertThat(initialUpdatedAt).isAfterOrEqualTo(createdAt);
        assertThat(initialResolved.updatedAt()).isEqualTo(initialUpdatedAt);
        assertThat(updated.createdAt()).isEqualTo(createdAt);
        assertThat(updated.updatedAt()).isAfter(initialUpdatedAt);
        assertThat(updatedResolved.statement().id()).isEqualTo(statementId);
        assertThat(updatedResolved.updatedAt()).isEqualTo(updated.updatedAt());
    }
}

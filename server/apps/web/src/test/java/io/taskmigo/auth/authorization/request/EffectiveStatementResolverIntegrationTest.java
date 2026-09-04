package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.StatementService;
import io.taskmigo.auth.role.RoleAuthorizationService;
import io.taskmigo.auth.user.UserService;
import io.taskmigo.rest.api.v0.testing.ApiIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EffectiveStatementResolverIntegrationTest extends ApiIntegrationTestSupport {

    private final EffectiveStatementResolver resolver;
    private final StatementService statements;
    private final RoleAuthorizationService roleAssignments;
    private final UserService users;
    private final Statistics statistics;

    EffectiveStatementResolverIntegrationTest(
        EffectiveStatementResolver resolver,
        StatementService statements,
        RoleAuthorizationService roleAssignments,
        UserService users,
        EntityManagerFactory entityManagerFactory
    ) {
        this.resolver = resolver;
        this.statements = statements;
        this.roleAssignments = roleAssignments;
        this.users = users;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    /**
     * Verifies that effective Statement resolution stays targeted as unrelated authorization data grows.
     *
     * Given: a User with one Role containing 500 Statements, followed by 100 unrelated Roles.
     * Expect: both resolutions return 500 Statements and perform the same bounded number of SQL statements.
     */
    @Test
    @DisplayName("keeps effective statement resolution bounded as unrelated roles grow")
    void shouldKeepQueryCountBoundedWhenUnrelatedRolesAreAdded() {
        // Arrange
        List<UUID> statementIds = this.createStatements(500);
        UUID roleId = this.roleAssignments.reconcile("performance-role-" + UUID.randomUUID(), null, statementIds);
        UUID userId = this.users.create(
            "performance-user-" + UUID.randomUUID(),
            Set.of("performance-" + UUID.randomUUID() + "@example.com"),
            "Performance",
            "User",
            List.of(roleId)
        );

        // Act
        this.statistics.clear();
        List<StatementInfo> baseline = this.resolver.resolve(userId);
        long baselineQueries = this.statistics.getPrepareStatementCount();
        this.createUnrelatedRoles(100);
        this.statistics.clear();
        List<StatementInfo> afterGrowth = this.resolver.resolve(userId);
        long afterGrowthQueries = this.statistics.getPrepareStatementCount();

        // Assert
        assertThat(baseline).hasSize(500);
        assertThat(afterGrowth).hasSize(500);
        assertThat(baselineQueries).isLessThanOrEqualTo(12);
        assertThat(afterGrowthQueries).isEqualTo(baselineQueries);
    }

    private UUID createStatement(String name) {
        return this.statements.create(
            name,
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/performance",
            "export default () => true;"
        );
    }

    private List<UUID> createStatements(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> this.createStatement("performance-statement-" + index + "-" + UUID.randomUUID()))
            .toList();
    }

    private void createUnrelatedRoles(int count) {
        IntStream.range(0, count).forEach(index ->
            this.roleAssignments.reconcile("unrelated-role-" + index + "-" + UUID.randomUUID(), null, Set.of())
        );
    }
}

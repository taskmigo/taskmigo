package io.taskmigo.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.role.RoleAuthorizationService;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.identity.user.application.port.in.api.UserRegistrationService;
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
    private final RoleService roles;
    private final RoleAuthorizationService roleAssignments;
    private final UserRegistrationService users;
    private final Statistics statistics;

    EffectiveStatementResolverIntegrationTest(
        EffectiveStatementResolver resolver,
        StatementService statements,
        RoleService roles,
        RoleAuthorizationService roleAssignments,
        UserRegistrationService users,
        EntityManagerFactory entityManagerFactory
    ) {
        this.resolver = resolver;
        this.statements = statements;
        this.roles = roles;
        this.roleAssignments = roleAssignments;
        this.users = users;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("keeps effective statement resolution bounded as unrelated roles grow")
    void shouldKeepQueryCountBoundedWhenUnrelatedRolesAreAdded() {
        List<UUID> statementIds = this.createStatements(500);
        String roleCode = "performance-role-" + UUID.randomUUID();
        UUID roleId = this.roles.createRole(roleCode, roleCode, null, Set.of());
        this.roleAssignments.setStatements(roleId, statementIds);
        UUID userId = this.users.register(
            "performance-user-" + UUID.randomUUID(),
            Set.of("performance-" + UUID.randomUUID() + "@example.com"),
            "Performance",
            "User",
            List.of(roleId),
            Set.of()
        );

        this.statistics.clear();
        List<EffectiveStatement> baseline = this.resolver.resolve(userId);
        long baselineQueries = this.statistics.getPrepareStatementCount();
        this.createUnrelatedRoles(100);
        this.statistics.clear();
        List<EffectiveStatement> afterGrowth = this.resolver.resolve(userId);
        long afterGrowthQueries = this.statistics.getPrepareStatementCount();

        assertThat(baseline).hasSize(500);
        assertThat(afterGrowth).hasSize(500);
        assertThat(baselineQueries).isLessThanOrEqualTo(12);
        assertThat(afterGrowthQueries).isEqualTo(baselineQueries);
    }

    private UUID createStatement(String name) {
        return this.statements.create(name, null, Effect.ALLOW, Scope.REQUEST, "GET", "/performance", "return true;");
    }

    private List<UUID> createStatements(int count) {
        return IntStream.range(0, count)
            .mapToObj(index -> this.createStatement("performance-statement-" + index + "-" + UUID.randomUUID()))
            .toList();
    }

    private void createUnrelatedRoles(int count) {
        IntStream.range(0, count).forEach(index -> {
            String roleCode = "unrelated-role-" + index + "-" + UUID.randomUUID();
            this.roles.createRole(roleCode, roleCode, null, Set.of());
        });
    }
}

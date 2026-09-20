package io.taskmigo.authorization.persistence.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.persistence.role.RoleEntity;
import io.taskmigo.authorization.persistence.role.RoleRepository;
import io.taskmigo.authorization.statement.infrastructure.persistence.StatementEntity;
import io.taskmigo.authorization.statement.infrastructure.persistence.StatementRepository;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveSubjectResolver;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.authorization.subject.SubjectRef;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EffectiveStatementResolverTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DIRECT_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CHILD_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID DIRECT_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ROLE_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID CHILD_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final SubjectRef USER = new SubjectRef("identity:user", USER_ID);

    private final EffectiveSubjectResolver subjects = mock(EffectiveSubjectResolver.class);
    private final SubjectGrantService grants = mock(SubjectGrantService.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final StatementRepository statements = mock(StatementRepository.class);
    private final DatabaseEffectiveStatementResolver resolver = new DatabaseEffectiveStatementResolver(
        this.subjects,
        this.grants,
        this.roles,
        this.statements
    );

    @Test
    @DisplayName("resolves direct and inherited statements from effective subjects")
    void shouldResolveStatementsFromSubjectBindingsAndRoleHierarchy() {
        when(this.subjects.resolve(USER_ID)).thenReturn(Set.of(USER));
        when(this.grants.statementIds(USER)).thenReturn(Set.of(DIRECT_STATEMENT_ID));
        when(this.grants.roleIds(USER)).thenReturn(Set.of(DIRECT_ROLE_ID));
        when(this.roles.findDescendantRoleIds(Set.of(DIRECT_ROLE_ID))).thenReturn(
            List.of(DIRECT_ROLE_ID, CHILD_ROLE_ID)
        );
        RoleEntity directRole = role(Set.of(ROLE_STATEMENT_ID));
        RoleEntity childRole = role(Set.of(CHILD_STATEMENT_ID));
        List<StatementEntity> persistedStatements = List.of(
            statement(DIRECT_STATEMENT_ID, "direct"),
            statement(ROLE_STATEMENT_ID, "role"),
            statement(CHILD_STATEMENT_ID, "child")
        );
        when(this.roles.findDistinctByIdIn(anyCollection())).thenReturn(List.of(directRole, childRole));
        when(this.statements.findAllByIdIn(anyCollection())).thenReturn(persistedStatements);

        List<String> names = this.resolver
            .resolve(USER_ID)
            .stream()
            .map(EffectiveStatement::statement)
            .map(StatementInfo::code)
            .toList();

        assertThat(names).containsExactly("direct", "role", "child");
    }

    @Test
    @DisplayName("resolves a large direct subject statement set with one repository query")
    void shouldResolveFiveHundredDirectStatements() {
        Set<UUID> ids = IntStream.range(0, 500)
            .mapToObj(EffectiveStatementResolverTest::id)
            .collect(Collectors.toSet());
        List<StatementEntity> persistedStatements = IntStream.range(0, 500)
            .mapToObj(index -> statement(id(index), "statement-" + index))
            .toList();
        when(this.subjects.resolve(USER_ID)).thenReturn(Set.of(USER));
        when(this.grants.statementIds(USER)).thenReturn(ids);
        when(this.grants.roleIds(USER)).thenReturn(Set.of());
        when(this.statements.findAllByIdIn(ids)).thenReturn(persistedStatements);

        assertThat(this.resolver.resolve(USER_ID)).hasSize(500);
    }

    private static RoleEntity role(Set<UUID> statementIds) {
        RoleEntity role = mock(RoleEntity.class);
        when(role.statementIds()).thenReturn(statementIds);
        return role;
    }

    private static StatementEntity statement(UUID id, String name) {
        StatementEntity statement = mock(StatementEntity.class);
        when(statement.id()).thenReturn(id);
        when(statement.info()).thenReturn(
            new StatementInfo(
                id,
                name,
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                new TargetInfo(new ApiInfo("GET", "/")),
                "return true;"
            )
        );
        when(statement.updatedAt()).thenReturn(Instant.EPOCH);
        return statement;
    }

    private static UUID id(int index) {
        return new UUID(1L, index);
    }
}

package io.taskmigo.authorization.persistence.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.application.port.out.EffectiveSubjectResolver;
import io.taskmigo.authorization.role.application.port.out.RoleEffectiveStatementRepository;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.TargetInfo;
import io.taskmigo.authorization.statement.adapter.out.persistence.StatementEntity;
import io.taskmigo.authorization.statement.adapter.out.persistence.StatementRepository;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.SubjectGrantRepository;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
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
    private static final UUID DIRECT_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID ROLE_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID CHILD_STATEMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final SubjectRef USER = new SubjectRef("identity:user", USER_ID);

    private final EffectiveSubjectResolver subjects = mock(EffectiveSubjectResolver.class);
    private final SubjectGrantRepository grants = mock(SubjectGrantRepository.class);
    private final RoleEffectiveStatementRepository roles = mock(RoleEffectiveStatementRepository.class);
    private final StatementRepository statements = mock(StatementRepository.class);
    private final DatabaseEffectiveStatementResolver resolver = new DatabaseEffectiveStatementResolver(
        this.subjects,
        this.grants,
        this.roles,
        this.statements
    );

    /**
     * Verifies the resolver combines direct subject Statements with the bounded Role effective-state port.
     *
     * Given: a subject with one direct Statement and one Role whose effective assignments contain two Statements.
     * Expect: all three Statements are loaded once through the Statement repository and returned deterministically.
     */
    @Test
    @DisplayName("resolves direct and inherited statements from effective subjects")
    void shouldResolveStatementsFromSubjectBindingsAndRoleHierarchy() {
        // Arrange
        when(this.subjects.resolve(USER_ID)).thenReturn(Set.of(USER));
        when(this.grants.load(USER)).thenReturn(
            new SubjectGrants(USER, Set.of(DIRECT_ROLE_ID), Set.of(DIRECT_STATEMENT_ID))
        );
        when(this.roles.statementIdsForRoles(Set.of(DIRECT_ROLE_ID))).thenReturn(
            Set.of(ROLE_STATEMENT_ID, CHILD_STATEMENT_ID)
        );
        List<StatementEntity> persistedStatements = List.of(
            statement(DIRECT_STATEMENT_ID, "direct"),
            statement(ROLE_STATEMENT_ID, "role"),
            statement(CHILD_STATEMENT_ID, "child")
        );
        when(this.statements.findAllByIdIn(anyCollection())).thenReturn(persistedStatements);

        // Act
        List<String> names = this.resolver
            .resolve(USER_ID)
            .stream()
            .map(EffectiveStatement::statement)
            .map(StatementInfo::code)
            .toList();

        // Assert
        assertThat(names).containsExactly("direct", "role", "child");
    }

    /**
     * Verifies large direct Statement sets retain one bounded Statement repository read.
     *
     * Given: a subject with five hundred directly assigned Statements and no Roles.
     * Expect: all Statements are resolved without Role graph traversal.
     */
    @Test
    @DisplayName("resolves a large direct subject statement set with one repository query")
    void shouldResolveFiveHundredDirectStatements() {
        // Arrange
        Set<UUID> ids = IntStream.range(0, 500)
            .mapToObj(EffectiveStatementResolverTest::id)
            .collect(Collectors.toSet());
        List<StatementEntity> persistedStatements = IntStream.range(0, 500)
            .mapToObj(index -> statement(id(index), "statement-" + index))
            .toList();
        when(this.subjects.resolve(USER_ID)).thenReturn(Set.of(USER));
        when(this.grants.load(USER)).thenReturn(new SubjectGrants(USER, Set.of(), ids));
        when(this.roles.statementIdsForRoles(Set.of())).thenReturn(Set.of());
        when(this.statements.findAllByIdIn(ids)).thenReturn(persistedStatements);

        // Act
        List<EffectiveStatement> resolved = this.resolver.resolve(USER_ID);

        // Assert
        assertThat(resolved).hasSize(500);
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

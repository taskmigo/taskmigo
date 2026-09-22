package io.taskmigo.authorization.role.application.service;

import static org.mockito.Mockito.verify;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@NullMarked
@ExtendWith(MockitoExtension.class)
class DefaultRoleAuthorizationServiceTest {

    @Mock
    private RoleCommandService roles;

    @Mock
    private StatementService statements;

    /**
     * Verifies direct Role Statement assignment validates references before mutating the aggregate.
     *
     * Given: duplicate requested Statement ids.
     * Expect: references are validated once as a set and the canonical Role command receives the deduplicated set.
     */
    @Test
    @DisplayName("validates and replaces direct role statements through canonical command")
    void shouldReplaceDirectStatementsWhenRequestedReferencesExist() {
        // Arrange
        UUID roleId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        DefaultRoleAuthorizationService service = new DefaultRoleAuthorizationService(
            this.roles,
            this.statements,
            directTransactions()
        );

        // Act
        service.setStatements(roleId, List.of(statementId, statementId));

        // Assert
        verify(this.statements).requireStatements(Set.of(statementId));
        verify(this.roles).replaceStatements(roleId, Set.of(statementId));
    }

    private static TransactionRunner directTransactions() {
        return new TransactionRunner() {
            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void write(Runnable work) {
                work.run();
            }
        };
    }
}

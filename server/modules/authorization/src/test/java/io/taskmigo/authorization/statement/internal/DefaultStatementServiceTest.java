package io.taskmigo.authorization.statement.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/// Verifies Statement use-case orchestration without a persistence implementation.
@ExtendWith(MockitoExtension.class)
class DefaultStatementServiceTest {

    @Mock
    private StatementStore statements;

    @Mock
    private StatementPolicyValidator policyValidator;

    @InjectMocks
    private DefaultStatementService service;

    /// Given a duplicate validated code, expects creation to stop before persistence changes.
    @Test
    @DisplayName("create rejects duplicate codes")
    void createRejectsDuplicateCodes() {
        // Arrange
        StatementDefinition definition = definition("projects.read");
        when(
            this.policyValidator.validate(
                "projects.read",
                null,
                Effect.ALLOW,
                Scope.REQUEST,
                "GET",
                "/projects",
                "true"
            )
        ).thenReturn(definition);
        when(this.statements.existsByCode("projects.read")).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() ->
            this.service.create("projects.read", null, Effect.ALLOW, Scope.REQUEST, "GET", "/projects", "true")
        )
            .isInstanceOf(AuthorizationException.class)
            .hasMessage("Statement code already exists");
        verify(this.statements).existsByCode("projects.read");
    }

    private static StatementDefinition definition(String name) {
        return new StatementDefinition(name, null, Effect.ALLOW, Scope.REQUEST, "GET", "/projects", "true");
    }
}

package io.taskmigo.identity.authorization.statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementPolicyValidator;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.persistence.statement.StatementEntity;
import io.taskmigo.identity.persistence.statement.StatementRepository;
import io.taskmigo.language.LanguageCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class StatementActivationResultTypeTest {

    /**
     * Verifies that a valid Request policy with a non-Boolean result is accepted during activation.
     *
     * Given: a syntactically valid Request policy returning a String.
     * Expect: Statement activation saves the row and defers Boolean enforcement to runtime.
     */
    @Test
    @DisplayName("activates a valid request statement without requiring a boolean result type")
    void shouldActivateRequestStatementWithNonBooleanProgramResult() {
        // Arrange
        StatementRepository repository = mock(StatementRepository.class);
        when(repository.existsByName("non_boolean_request")).thenReturn(false);
        StatementService service = new StatementService(
            repository,
            new StatementPolicyValidator(mock(ObjectAuthorization.class), new LanguageCompiler()),
            mock(QueryPredicateBinder.class),
            mock(ObjectAuthorizationPredicateBinder.class)
        );

        // Act
        service.create(
            "non_boolean_request",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/api/v0/users",
            "return \"not-a-decision-yet\";"
        );

        // Assert
        verify(repository).save(ArgumentMatchers.any(StatementEntity.class));
    }
}

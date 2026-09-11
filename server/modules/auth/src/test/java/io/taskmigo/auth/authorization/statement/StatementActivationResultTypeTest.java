package io.taskmigo.auth.authorization.statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.object.ObjectAuthorization;
import io.taskmigo.auth.resourcequery.ObjectAuthorizationPredicateBinder;
import io.taskmigo.auth.resourcequery.QueryPredicateBinder;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
            mock(ObjectAuthorization.class),
            new EmbeddedLanguageCompiler(),
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
        verify(repository).save(org.mockito.ArgumentMatchers.any(StatementEntity.class));
    }
}

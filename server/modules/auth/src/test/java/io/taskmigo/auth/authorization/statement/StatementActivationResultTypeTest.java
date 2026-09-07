package io.taskmigo.auth.authorization.statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StatementActivationResultTypeTest {

    @Test
    @DisplayName("activates a valid request statement without requiring a boolean result type")
    void shouldActivateRequestStatementWithNonBooleanProgramResult() {
        StatementRepository repository = mock(StatementRepository.class);
        when(repository.existsByName("non_boolean_request")).thenReturn(false);
        StatementService service = new StatementService(
            repository,
            mock(ObjectAuthorizationService.class),
            new EmbeddedLanguageCompiler()
        );

        service.create(
            "non_boolean_request",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/api/v0/users",
            "return \"not-a-decision-yet\";"
        );

        verify(repository).save(org.mockito.ArgumentMatchers.any(StatementEntity.class));
    }
}

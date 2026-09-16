package io.taskmigo.authorization.statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.persistence.statement.StatementEntity;
import io.taskmigo.authorization.persistence.statement.StatementRepository;
import io.taskmigo.language.LanguageCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class StatementActivationResultTypeTest {

    @Test
    @DisplayName("activates a valid request statement without requiring a boolean result type")
    void shouldActivateRequestStatementWithNonBooleanProgramResult() {
        StatementRepository repository = mock(StatementRepository.class);
        when(repository.existsByName("non_boolean_request")).thenReturn(false);
        StatementService service = new StatementService(
            repository,
            new StatementPolicyValidator(mock(ObjectAuthorization.class), new LanguageCompiler()),
            mock(QueryPredicateBinder.class),
            mock(ObjectAuthorizationPredicateBinder.class)
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

        verify(repository).save(ArgumentMatchers.any(StatementEntity.class));
    }
}

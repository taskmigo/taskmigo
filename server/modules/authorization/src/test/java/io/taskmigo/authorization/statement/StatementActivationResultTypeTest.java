package io.taskmigo.authorization.statement;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.object.ObjectAuthorization;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.authorization.persistence.query.QueryPredicateBinder;
import io.taskmigo.authorization.persistence.statement.StatementEntity;
import io.taskmigo.authorization.persistence.statement.StatementRepository;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QueryPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.jpa.domain.Specification;

class StatementActivationResultTypeTest {

    @Test
    @DisplayName("activates a valid request statement without requiring a boolean result type")
    void shouldActivateRequestStatementWithNonBooleanProgramResult() {
        StatementRepository repository = mock(StatementRepository.class);
        when(repository.existsByName("non_boolean_request")).thenReturn(false);
        StatementService service = new StatementService(
            repository,
            new StatementPolicyValidator(mock(ObjectAuthorization.class), new LanguageCompiler()),
            new QueryBinderStub(),
            new ObjectBinderStub()
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

    private static final class QueryBinderStub implements QueryPredicateBinder<StatementInfo, StatementEntity> {

        @Override
        public Class<StatementInfo> queryType() {
            return StatementInfo.class;
        }

        @Override
        public Class<StatementEntity> domainType() {
            return StatementEntity.class;
        }

        @Override
        public Specification<StatementEntity> bind(QueryPredicate<StatementInfo> predicate) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ObjectBinderStub
        implements ObjectAuthorizationPredicateBinder<StatementInfo, StatementEntity>
    {

        @Override
        public Class<StatementInfo> objectType() {
            return StatementInfo.class;
        }

        @Override
        public Class<StatementEntity> domainType() {
            return StatementEntity.class;
        }

        @Override
        public Specification<StatementEntity> bind(ObjectAuthorizationPredicate<StatementInfo> predicate) {
            throw new UnsupportedOperationException();
        }
    }
}

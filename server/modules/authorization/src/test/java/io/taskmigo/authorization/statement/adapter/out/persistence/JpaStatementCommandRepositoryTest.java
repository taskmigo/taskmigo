package io.taskmigo.authorization.statement.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementException;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.foundation.DomainFailureType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JpaStatementCommandRepositoryTest {

    @Mock
    private StatementRepository statements;

    @InjectMocks
    private JpaStatementCommandRepository repository;

    /**
     * Verifies that a database integrity failure during Statement insertion becomes a conflict while retaining its cause.
     *
     * Given: a new Statement whose insert is rejected by the persistence repository with a data-integrity violation.
     * Expect: save throws a Statement conflict whose direct cause is the original persistence exception.
     */
    @Test
    @DisplayName("translates statement insert integrity violations to conflicts")
    void shouldThrowConflictWhenStatementInsertViolatesIntegrity() {
        // Arrange
        Statement statement = Statement.create(
            UUID.randomUUID(),
            "duplicate_statement",
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/api/v0/statements",
            "return true;"
        );
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate statement code");
        when(this.statements.findById(statement.id())).thenReturn(Optional.empty());
        when(this.statements.saveAndFlush(any(StatementEntity.class))).thenThrow(cause);

        // Act + Assert
        assertThatThrownBy(() -> this.repository.save(statement))
            .isInstanceOfSatisfying(StatementException.class, exception -> {
                assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT);
                assertThat(exception).hasMessage("Statement code already exists");
                assertThat(exception.getCause()).isSameAs(cause);
            });
    }
}

package io.taskmigo.authorization.statement.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementException;
import io.taskmigo.authorization.statement.domain.Statement;
import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.web.adapter.in.http.api.v0.testing.ApiIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class StatementUniqueConstraintIntegrationTest extends ApiIntegrationTestSupport {

    private final JpaStatementCommandRepository statements;

    StatementUniqueConstraintIntegrationTest(JpaStatementCommandRepository statements) {
        this.statements = statements;
    }

    /**
     * Verifies that concurrent Statement writers rely on the database uniqueness constraint without leaking a
     * persistence failure.
     *
     * Given: two distinct runtime Statement aggregates with the same code are released to the JPA adapter concurrently.
     * Expect: exactly one insert succeeds and the losing writer receives a conflict retaining the database exception.
     */
    @Test
    @DisplayName("translates the losing concurrent statement writer to a conflict")
    void shouldTranslateConflictWhenConcurrentStatementsUseSameCode() throws Exception {
        // Arrange
        String code = "race-" + UUID.randomUUID();
        Statement first = statement(code);
        Statement second = statement(code);
        CyclicBarrier start = new CyclicBarrier(2);

        // Act
        List<SaveOutcome> outcomes;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<SaveOutcome> firstResult = executor.submit(() -> this.saveAfterBarrier(first, start));
            Future<SaveOutcome> secondResult = executor.submit(() -> this.saveAfterBarrier(second, start));
            outcomes = List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
        }

        // Assert
        assertThat(outcomes).filteredOn(Saved.class::isInstance).hasSize(1);
        assertThat(outcomes)
            .filteredOn(Conflict.class::isInstance)
            .singleElement()
            .isInstanceOfSatisfying(Conflict.class, conflict -> {
                assertThat(conflict.exception().type()).isEqualTo(DomainFailureType.CONFLICT);
                assertThat(conflict.exception()).hasMessage("Statement code already exists");
                assertThat(conflict.exception().getCause()).isInstanceOf(DataIntegrityViolationException.class);
            });
    }

    private SaveOutcome saveAfterBarrier(Statement statement, CyclicBarrier start) throws Exception {
        start.await();
        try {
            this.statements.save(statement);
            return new Saved();
        } catch (StatementException exception) {
            return new Conflict(exception);
        }
    }

    private static Statement statement(String code) {
        return Statement.create(
            UUID.randomUUID(),
            code,
            null,
            Effect.ALLOW,
            Scope.REQUEST,
            "GET",
            "/api/v0/statements",
            "return true;"
        );
    }

    private sealed interface SaveOutcome permits Saved, Conflict {}

    private record Saved() implements SaveOutcome {}

    private record Conflict(StatementException exception) implements SaveOutcome {}
}

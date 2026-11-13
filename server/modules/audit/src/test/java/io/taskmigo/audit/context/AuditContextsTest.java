package io.taskmigo.audit.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.audit.model.AuditActor;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditContextsTest {

    /**
     * Verifies that a value-returning use case carries and then restores actor attribution.
     *
     * Given: a User actor bound to a Worker job execution.
     * Expect: the result is preserved and the context is removed after the call.
     */
    @Test
    @DisplayName("restores attribution after a value returning execution")
    void shouldReturnValueWhenContextScopeEnds() {
        // Arrange
        AuditContext context = AuditContext.user(
            UUID.randomUUID(),
            "alice",
            AuditExecutor.worker("worker"),
            AuditCause.job("77")
        );

        // Act
        String result = AuditContexts.call(context, () -> AuditContexts.require().actor().username());

        // Assert
        assertThat(result).isEqualTo("alice");
        assertThat(AuditContexts.isBound()).isFalse();
    }

    /**
     * Verifies the original User actor is captured at task submission, not inferred from the worker.
     *
     * Given: a User-originated operation that submits a Callable to a separate thread.
     * Expect: the same actor on the executor thread, with no leaked context afterwards.
     */
    @Test
    @DisplayName("captures and restores the original actor across an async boundary")
    void shouldPreserveOriginalActorOnExecutorThread() throws Exception {
        // Arrange
        AuditContext user = AuditContext.user(
            UUID.randomUUID(),
            "alice",
            AuditExecutor.web("web"),
            AuditCause.request("1")
        );
        try (var executor = Executors.newSingleThreadExecutor()) {
            // Act
            var result = AuditContexts.call(user, () ->
                executor.submit(AuditContexts.captureCallable(() -> AuditContexts.require().actor()))
            );

            // Assert
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(user.actor());
            assertThat(AuditContexts.isBound()).isFalse();
            assertThat(executor.submit((Callable<Boolean>) AuditContexts::isBound).get(10, TimeUnit.SECONDS)).isFalse();
        }
    }

    /**
     * Verifies that a Web-to-Worker task keeps its User actor and declares its actual executor.
     *
     * Given: a User request schedules a job that runs on a different executor.
     * Expect: the original User remains responsible while executor and causation identify the job.
     */
    @Test
    @DisplayName("retains User actor while changing executor and causation for async work")
    void shouldAttributeUserInitiatedWorkerExecution() throws Exception {
        // Arrange
        AuditContext user = AuditContext.user(
            UUID.randomUUID(),
            "alice",
            AuditExecutor.web("web"),
            AuditCause.request("request-1")
        );

        try (var executor = Executors.newSingleThreadExecutor()) {
            // Act
            var future = AuditContexts.call(user, () ->
                executor.submit(
                    AuditContexts.captureCallable(
                        AuditExecutor.worker("worker"),
                        AuditCause.job("job-42"),
                        AuditContexts::require
                    )
                )
            );
            AuditContext observed = future.get(10, TimeUnit.SECONDS);

            // Assert
            assertThat(observed.actor()).isEqualTo(user.actor());
            assertThat(observed.executor()).isEqualTo(AuditExecutor.worker("worker"));
            assertThat(observed.causation()).isEqualTo(AuditCause.job("job-42"));
            assertThat(AuditContexts.isBound()).isFalse();
        }
    }

    /**
     * Verifies that missing attribution is never silently replaced with a System identity.
     *
     * Given: no trusted entry point declared an actor.
     * Expect: audit context lookup fails rather than attributing the call to SYSTEM.
     */
    @Test
    @DisplayName("rejects audited operations without an actor")
    void shouldRejectAuditWhenContextIsMissing() {
        // Arrange + Act + Assert
        assertThatThrownBy(AuditContexts::require).isInstanceOf(IllegalStateException.class);
    }

    /**
     * Verifies that nested executions restore the prior actor even when inner work throws.
     *
     * Given: a User job executing inside Worker temporarily invokes a SYSTEM sub-operation.
     * Expect: responsibility stays with the original User after the inner scope ends.
     */
    @Test
    @DisplayName("restores attribution after nested or failed operations")
    void shouldRestoreActorWhenNestedExecutionFails() {
        // Arrange
        AuditContext user = new AuditContext(
            new AuditActor(UUID.randomUUID(), "alice"),
            AuditExecutor.worker("worker"),
            AuditCause.job("1")
        );
        AuditContext system = AuditContext.system(
            "retention-purge",
            AuditExecutor.worker("worker"),
            AuditCause.scheduled("1")
        );

        // Act
        AuditContexts.run(user, () -> {
            assertThat(AuditContexts.require()).isEqualTo(user);
            assertThatThrownBy(() ->
                AuditContexts.run(system, () -> {
                    assertThat(AuditContexts.require()).isEqualTo(system);
                    throw new IllegalStateException("inner failure");
                })
            )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("inner failure");
            assertThat(AuditContexts.require()).isEqualTo(user);
        });

        // Assert
        assertThatThrownBy(AuditContexts::require).isInstanceOf(IllegalStateException.class);
        assertThat(AuditActor.system("retention-purge")).isEqualTo(system.actor());
    }
}

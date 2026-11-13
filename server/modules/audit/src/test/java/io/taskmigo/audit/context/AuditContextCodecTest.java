package io.taskmigo.audit.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.audit.model.AuditActor;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditContextCodecTest {

    /**
     * Verifies that persisted job provenance retains the initiating User rather than the Worker process.
     *
     * Given: a User-originated command running through Worker with a job correlation identifier.
     * Expect: encoding and decoding the trusted job metadata preserves typed actor and causation.
     */
    @Test
    @DisplayName("round trips a User actor and typed job provenance")
    void shouldPreserveOriginalActorWhenJobContextIsSerialized() {
        // Arrange
        AuditContext context = AuditContext.user(
            UUID.randomUUID(),
            "alice",
            AuditExecutor.worker("worker"),
            AuditCause.job("job-123")
        );

        // Act
        String encoded = AuditContextCodec.encode(context);
        AuditContext decoded = AuditContextCodec.decodeTrusted(encoded);

        // Assert
        assertThat(decoded).isEqualTo(context);
        assertThat(decoded.actor().type()).isEqualTo(AuditActor.Type.USER);
        assertThat(decoded.causation().persistedValue()).isEqualTo("ASYNC_JOB:job-123");
    }

    /**
     * Verifies unknown or future schema versions are rejected rather than silently misattributed.
     *
     * Given: an encoded envelope containing a future unsupported schema version.
     * Expect: the decoder fails clearly instead of treating it as a trusted User or System actor.
     */
    @Test
    @DisplayName("rejects unsupported audit context envelope versions")
    void shouldRejectUnknownEnvelopeVersions() {
        // Arrange
        AuditContext context = AuditContext.system(
            "migration",
            AuditExecutor.migration("migration"),
            AuditCause.installation("startup")
        );
        String encoded = AuditContextCodec.encode(context).replace("\"version\":1", "\"version\":2");

        // Act + Assert
        assertThatThrownBy(() -> AuditContextCodec.decodeTrusted(encoded))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported audit context version");
    }
}

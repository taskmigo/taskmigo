package io.taskmigo.eav.schema.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavSchemaVersionTest {

    @Test
    @DisplayName("exposes exactly the supported EAV data types")
    void shouldExposeExactlyTheSupportedDataTypes() {
        assertThat(EavDataType.values())
            .extracting(Enum::name)
            .containsExactly("STRING", "NUMBER", "BOOLEAN", "DATE", "DATETIME", "UUID", "ENUM");
    }

    @Test
    @DisplayName("allows replacing fields only while a schema version is draft")
    void shouldAllowReplacingFieldsOnlyWhileDraft() {
        EavSchemaVersion version = EavSchemaVersion.draft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            List.of(field("severity", EavDataType.STRING))
        );
        EavFieldDefinition replacement = field("priority", EavDataType.NUMBER);

        version.replaceFields(List.of(replacement));
        assertThat(version.fields()).containsExactly(replacement);

        version.publish(Instant.parse("2026-10-15T00:00:00Z"));
        assertThat(version.status()).isEqualTo(EavSchemaStatus.PUBLISHED);
        assertThatThrownBy(() -> version.replaceFields(List.of(field("other", EavDataType.STRING))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("draft");
    }

    @Test
    @DisplayName("enforces the draft published retired lifecycle")
    void shouldEnforceDraftPublishedRetiredLifecycle() {
        EavSchemaVersion draft = EavSchemaVersion.draft(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            List.of(field("severity", EavDataType.STRING))
        );
        Instant publishedAt = Instant.parse("2026-10-15T00:00:00Z");
        Instant retiredAt = Instant.parse("2026-10-16T00:00:00Z");

        assertThatThrownBy(() -> draft.retire(retiredAt)).isInstanceOf(IllegalStateException.class);
        draft.publish(publishedAt);
        assertThat(draft.publishedAt()).contains(publishedAt);
        assertThatThrownBy(() -> draft.publish(publishedAt)).isInstanceOf(IllegalStateException.class);

        draft.retire(retiredAt);
        assertThat(draft.status()).isEqualTo(EavSchemaStatus.RETIRED);
        assertThat(draft.retiredAt()).contains(retiredAt);
    }

    @Test
    @DisplayName("rejects constraint names unsupported by the declared field type")
    void shouldRejectUnsupportedConstraintNamesForTheFieldType() {
        assertThatThrownBy(() ->
            new EavFieldDefinition(
                UUID.randomUUID(),
                "severity",
                "Severity",
                EavDataType.STRING,
                false,
                Map.of("minimum", 1)
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("constraint");
    }

    private static EavFieldDefinition field(String key, EavDataType type) {
        return new EavFieldDefinition(UUID.randomUUID(), key, key, type, false, Map.of());
    }
}

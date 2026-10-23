package io.taskmigo.eav.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.query.QueryOperator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavQueryFieldCatalogTest {

    @Test
    @DisplayName("keeps one compatible logical field across historical versions and marks missing history nullable")
    void shouldResolveCompatibleHistoricalField() {
        UUID definitionId = UUID.randomUUID();
        UUID fieldId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = version(
            definitionId,
            1,
            EavSchemaStatus.RETIRED,
            List.of(field(fieldId, "severity", EavDataType.STRING, true))
        );
        EavSchemaVersionInfo v2 = version(definitionId, 2, EavSchemaStatus.PUBLISHED, List.of());

        Map<String, EavQueryField> fields = new EavQueryFieldCatalog().resolve(List.of(v1, v2));

        assertThat(fields).containsKey("severity");
        assertThat(fields.get("severity")).satisfies(field -> {
            assertThat(field.fieldId()).isEqualTo(fieldId);
            assertThat(field.dataType()).isEqualTo(EavDataType.STRING);
            assertThat(field.nullable()).isTrue();
            assertThat(field.operators()).isEqualTo(Set.of(QueryOperator.EQ, QueryOperator.NE, QueryOperator.CONTAINS));
            assertThat(field.sortable()).isTrue();
        });
    }

    @Test
    @DisplayName("ignores drafts and rejects the same public key when logical identity or type is ambiguous")
    void shouldFailClosedForAmbiguousKeys() {
        UUID definitionId = UUID.randomUUID();
        EavSchemaVersionInfo published = version(
            definitionId,
            1,
            EavSchemaStatus.PUBLISHED,
            List.of(field(UUID.randomUUID(), "severity", EavDataType.STRING, false))
        );
        EavSchemaVersionInfo retiredReplacement = version(
            definitionId,
            2,
            EavSchemaStatus.RETIRED,
            List.of(field(UUID.randomUUID(), "severity", EavDataType.ENUM, false))
        );
        EavSchemaVersionInfo draft = version(
            definitionId,
            3,
            EavSchemaStatus.DRAFT,
            List.of(field(UUID.randomUUID(), "draftOnly", EavDataType.NUMBER, false))
        );

        Map<String, EavQueryField> fields = new EavQueryFieldCatalog().resolve(
            List.of(published, retiredReplacement, draft)
        );

        assertThat(fields).doesNotContainKeys("severity", "draftOnly");
    }

    @Test
    @DisplayName("same key with different logical identities across schema definitions is globally ambiguous")
    void shouldRejectCrossDefinitionIdentityAmbiguity() {
        EavSchemaVersionInfo first = version(
            UUID.randomUUID(),
            1,
            EavSchemaStatus.PUBLISHED,
            List.of(field(UUID.randomUUID(), "customerTier", EavDataType.NUMBER, false))
        );
        EavSchemaVersionInfo second = version(
            UUID.randomUUID(),
            1,
            EavSchemaStatus.PUBLISHED,
            List.of(field(UUID.randomUUID(), "customerTier", EavDataType.NUMBER, false))
        );

        assertThat(new EavQueryFieldCatalog().resolve(List.of(first, second))).doesNotContainKey("customerTier");
    }

    private static EavFieldSpec field(UUID fieldId, String key, EavDataType type, boolean required) {
        return new EavFieldSpec(fieldId, key, key, type, required, Map.of());
    }

    private static EavSchemaVersionInfo version(
        UUID definitionId,
        long number,
        EavSchemaStatus status,
        List<EavFieldSpec> fields
    ) {
        Instant publishedAt = Instant.parse("2026-10-16T00:00:00Z");
        return new EavSchemaVersionInfo(
            UUID.randomUUID(),
            definitionId,
            number,
            status,
            fields,
            status == EavSchemaStatus.DRAFT ? Optional.empty() : Optional.of(publishedAt),
            status == EavSchemaStatus.RETIRED ? Optional.of(publishedAt.plusSeconds(60)) : Optional.empty()
        );
    }
}

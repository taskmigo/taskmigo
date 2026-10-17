package io.taskmigo.eav.value.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.eav.EavTestApplication;
import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.eav.value.application.EavInstanceView;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = EavTestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JpaEavInstanceRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final EavSchemaService schemas;
    private final EavInstanceService instances;

    JpaEavInstanceRepositoryIntegrationTest(EavSchemaService schemas, EavInstanceService instances) {
        this.schemas = schemas;
        this.instances = instances;
    }

    @Test
    @DisplayName("persists typed instance values and keeps historical schema semantics after retirement")
    void shouldPersistTypedValuesAndHistoricalSchema() {
        UUID definitionId = this.schemas.createDefinition("instance-jpa", "Instance");
        UUID severityId = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = this.schemas.createDraft(definitionId);
        v1 = this.schemas.replaceDraftFields(
            definitionId,
            v1.id(),
            List.of(
                new EavFieldSpec(severityId, "severity", "Severity", EavDataType.STRING, true, Map.of()),
                new EavFieldSpec(scoreId, "score", "Score", EavDataType.NUMBER, false, Map.of()),
                new EavFieldSpec(noteId, "note", "Note", EavDataType.STRING, false, Map.of())
            )
        );
        EavSchemaVersionInfo publishedV1 = this.schemas.publish(definitionId, v1.id());

        EavInstanceView created = this.instances.create(
            definitionId,
            publishedV1.id(),
            Map.of("severity", "legacy", "score", 7, "note", "remove")
        );
        assertThat(this.instances.get(created.id()).values())
            .containsEntry("severity", "legacy")
            .containsEntry("score", new BigDecimal("7"));

        Map<String, @Nullable Object> patch = new LinkedHashMap<>();
        patch.put("score", 8);
        patch.put("note", null);
        EavInstanceView patched = this.instances.patch(created.id(), patch);
        assertThat(patched.values())
            .containsEntry("severity", "legacy")
            .containsEntry("score", new BigDecimal("8"))
            .doesNotContainKey("note");

        EavSchemaVersionInfo v2 = this.schemas.createDraft(definitionId);
        v2 = this.schemas.replaceDraftFields(
            definitionId,
            v2.id(),
            List.of(
                new EavFieldSpec(
                    UUID.randomUUID(),
                    "severity",
                    "Severity",
                    EavDataType.ENUM,
                    true,
                    Map.of("options", List.of("HIGH", "LOW"))
                )
            )
        );
        this.schemas.publish(definitionId, v2.id());
        this.schemas.retire(definitionId, publishedV1.id());

        EavInstanceView historical = this.instances.patch(created.id(), Map.of("severity", "still-legacy"));
        assertThat(historical.schemaVersionId()).isEqualTo(publishedV1.id());
        assertThat(historical.values()).containsEntry("severity", "still-legacy");
        assertThat(this.instances.get(created.id())).isEqualTo(historical);
    }
}

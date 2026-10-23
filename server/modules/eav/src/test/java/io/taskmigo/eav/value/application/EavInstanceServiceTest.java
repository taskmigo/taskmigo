package io.taskmigo.eav.value.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaRepository;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaServiceImpl;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.schema.domain.EavValue;
import io.taskmigo.eav.schema.domain.EavValueValidator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavInstanceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    private final InMemorySchemaRepository schemas = new InMemorySchemaRepository();
    private final EavSchemaService schemaService = new EavSchemaServiceImpl(
        this.schemas,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );
    private final InMemoryInstanceRepository instances = new InMemoryInstanceRepository();
    private final EavInstanceService service = new EavInstanceServiceImpl(
        this.schemaService,
        this.instances,
        new EavValueValidator()
    );

    /**
     * Verifies that instance creation pins a published schema version and normalizes typed values.
     *
     * Given: a published schema with required and optional fields plus an optional null input.
     * Expect: the instance stores typed values for supplied data, omits the optional null, and keeps the version id.
     */
    @Test
    @DisplayName("creates a pinned instance with normalized typed values and omits optional nulls")
    void shouldCreatePinnedInstanceWithNormalizedValues() {
        // Arrange
        UUID definitionId = this.schemaService.createDefinition("bug", "Bug");
        UUID severityId = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        EavSchemaVersionInfo version = publish(
            definitionId,
            List.of(
                field(severityId, "severity", EavDataType.STRING, true, Map.of("minLength", 2)),
                field(scoreId, "score", EavDataType.NUMBER, false, Map.of()),
                field(noteId, "note", EavDataType.STRING, false, Map.of())
            )
        );

        // Act
        EavInstanceView created = this.service.create(
            definitionId,
            version.id(),
            mapOf("severity", "HIGH", "score", 7, "note", null)
        );

        // Assert
        assertThat(created.schemaDefinitionId()).isEqualTo(definitionId);
        assertThat(created.schemaVersionId()).isEqualTo(version.id());
        assertThat(created.values()).containsEntry("severity", "HIGH").containsEntry("score", new BigDecimal("7"));
        assertThat(created.values()).doesNotContainKey("note");
        assertThat(this.instances.find(created.id()))
            .get()
            .satisfies(stored -> {
                assertThat(stored.schemaVersionId()).isEqualTo(version.id());
                assertThat(stored.values()).containsEntry(severityId, new EavValue.StringValue("HIGH"));
                assertThat(stored.values()).containsEntry(scoreId, new EavValue.NumberValue(new BigDecimal("7")));
            });
    }

    /**
     * Verifies that invalid creation requests remain rejected after transaction plumbing is removed.
     *
     * Given: a draft schema, an unknown key, a missing required value, and a value violating minLength.
     * Expect: each request fails with the existing IllegalStateException or IllegalArgumentException behavior.
     */
    @Test
    @DisplayName("create rejects draft schemas, unknown keys, missing required values, and violated constraints")
    void shouldRejectInvalidCreateRequests() {
        // Arrange
        UUID definitionId = this.schemaService.createDefinition("incident", "Incident");
        UUID titleId = UUID.randomUUID();
        EavSchemaVersionInfo draft = this.schemaService.createDraft(definitionId);
        draft = this.schemaService.replaceDraftFields(
            definitionId,
            draft.id(),
            List.of(field(titleId, "summary", EavDataType.STRING, true, Map.of("minLength", 3)))
        );

        // Act + Assert
        EavSchemaVersionInfo finalDraft = draft;
        assertThatThrownBy(() ->
            this.service.create(definitionId, finalDraft.id(), Map.of("summary", "good"))
        ).isInstanceOf(IllegalStateException.class);

        EavSchemaVersionInfo published = this.schemaService.publish(definitionId, draft.id());
        assertThatThrownBy(() ->
            this.service.create(definitionId, published.id(), Map.of("unknown", "value"))
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> this.service.create(definitionId, published.id(), Map.of())).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() ->
            this.service.create(definitionId, published.id(), Map.of("summary", "x"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies patch semantics for omitted, optional-null, and required-null values.
     *
     * Given: a pinned instance with required summary plus optional note and score fields.
     * Expect: omitted values survive, optional null removes a value, score updates, and required null is rejected.
     */
    @Test
    @DisplayName("patch keeps omitted values, deletes optional nulls, and rejects required null deletion")
    void shouldApplyPatchSemantics() {
        // Arrange
        UUID definitionId = this.schemaService.createDefinition("task", "Task");
        EavSchemaVersionInfo version = publish(
            definitionId,
            List.of(
                field(UUID.randomUUID(), "summary", EavDataType.STRING, true, Map.of()),
                field(UUID.randomUUID(), "note", EavDataType.STRING, false, Map.of()),
                field(UUID.randomUUID(), "score", EavDataType.NUMBER, false, Map.of())
            )
        );
        EavInstanceView created = this.service.create(
            definitionId,
            version.id(),
            Map.of("summary", "Keep", "note", "remove", "score", 1)
        );

        // Act
        EavInstanceView patched = this.service.patch(created.id(), mapOf("note", null, "score", 2));

        // Assert
        assertThat(patched.values())
            .containsEntry("summary", "Keep")
            .containsEntry("score", new BigDecimal("2"))
            .doesNotContainKey("note");
        assertThatThrownBy(() -> this.service.patch(created.id(), mapOf("summary", null))).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    /**
     * Verifies historical schema pinning remains stable after newer publication and retirement.
     *
     * Given: an instance created from v1, followed by incompatible v2 publication and v1 retirement.
     * Expect: patch and get continue interpreting the instance with its pinned v1 schema.
     */
    @Test
    @DisplayName("patch and read keep using the pinned historical schema after a newer publish and retirement")
    void shouldUsePinnedSchemaAfterRetirement() {
        // Arrange
        UUID definitionId = this.schemaService.createDefinition("request", "Request");
        UUID legacyFieldId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = publish(
            definitionId,
            List.of(field(legacyFieldId, "severity", EavDataType.STRING, false, Map.of()))
        );
        EavInstanceView created = this.service.create(definitionId, v1.id(), Map.of("severity", "legacy"));

        EavSchemaVersionInfo v2 = this.schemaService.createDraft(definitionId);
        v2 = this.schemaService.replaceDraftFields(
            definitionId,
            v2.id(),
            List.of(
                field(UUID.randomUUID(), "severity", EavDataType.ENUM, false, Map.of("options", List.of("HIGH", "LOW")))
            )
        );
        this.schemaService.publish(definitionId, v2.id());
        this.schemaService.retire(definitionId, v1.id());

        // Act
        EavInstanceView patched = this.service.patch(created.id(), Map.of("severity", "still-legacy"));
        EavInstanceView read = this.service.get(created.id());

        // Assert
        assertThat(patched.schemaVersionId()).isEqualTo(v1.id());
        assertThat(patched.values()).containsEntry("severity", "still-legacy");
        assertThat(read).isEqualTo(patched);
    }

    private EavSchemaVersionInfo publish(UUID definitionId, List<EavFieldSpec> fields) {
        EavSchemaVersionInfo draft = this.schemaService.createDraft(definitionId);
        draft = this.schemaService.replaceDraftFields(definitionId, draft.id(), fields);
        return this.schemaService.publish(definitionId, draft.id());
    }

    private static EavFieldSpec field(
        UUID id,
        String key,
        EavDataType type,
        boolean required,
        Map<String, Object> constraints
    ) {
        return new EavFieldSpec(id, key, key, type, required, constraints);
    }

    private static Map<String, @Nullable Object> mapOf(@Nullable Object... entries) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            String key = (String) Objects.requireNonNull(entries[index]);
            values.put(key, entries[index + 1]);
        }
        return values;
    }

    private static final class InMemoryInstanceRepository implements EavInstanceRepository {

        private final Map<UUID, EavInstanceState> instances = new LinkedHashMap<>();

        @Override
        public void create(EavInstanceState state) {
            this.instances.put(state.id(), state);
        }

        @Override
        public Optional<EavInstanceState> find(UUID instanceId) {
            return Optional.ofNullable(this.instances.get(instanceId));
        }

        @Override
        public void update(EavInstanceState state) {
            this.instances.put(state.id(), state);
        }
    }

    private static final class InMemorySchemaRepository implements EavSchemaRepository {

        private final Map<UUID, Definition> definitions = new LinkedHashMap<>();
        private final Map<UUID, EavSchemaVersionInfo> versions = new LinkedHashMap<>();

        @Override
        public void createDefinition(UUID id, String key, String name) {
            this.definitions.put(id, new Definition(id, key, name, null));
        }

        @Override
        public boolean definitionExists(UUID definitionId) {
            return this.definitions.containsKey(definitionId);
        }

        @Override
        public void lockDefinition(UUID definitionId) {
            if (!this.definitionExists(definitionId)) {
                throw new IllegalArgumentException("Unknown EAV schema definition");
            }
        }

        @Override
        public long nextVersion(UUID definitionId) {
            return (
                this.versions
                    .values()
                    .stream()
                    .filter(v -> v.definitionId().equals(definitionId))
                    .count() + 1
            );
        }

        @Override
        public void insertVersion(EavSchemaVersionInfo version) {
            this.versions.put(version.id(), version);
        }

        @Override
        public void updateVersion(EavSchemaVersionInfo version) {
            this.versions.put(version.id(), version);
        }

        @Override
        public Optional<EavSchemaVersionInfo> findVersion(UUID definitionId, UUID versionId) {
            return Optional.ofNullable(this.versions.get(versionId)).filter(v -> v.definitionId().equals(definitionId));
        }

        @Override
        public List<EavSchemaVersionInfo> findVersions(UUID definitionId) {
            return this.versions
                .values()
                .stream()
                .filter(version -> version.definitionId().equals(definitionId))
                .sorted(Comparator.comparingLong(EavSchemaVersionInfo::version))
                .toList();
        }

        @Override
        public Optional<EavSchemaVersionInfo> current(UUID definitionId) {
            Definition definition = this.definitions.get(definitionId);
            if (definition == null || definition.currentVersionId() == null) {
                return Optional.empty();
            }
            return this.findVersion(definitionId, definition.currentVersionId());
        }

        @Override
        public Optional<EavFieldSpec> publishedField(UUID definitionId, UUID fieldId) {
            return this.versions
                .values()
                .stream()
                .filter(v -> v.definitionId().equals(definitionId))
                .filter(v -> v.status() != EavSchemaStatus.DRAFT)
                .flatMap(v -> v.fields().stream())
                .filter(field -> field.fieldId().equals(fieldId))
                .findFirst();
        }

        @Override
        public void setCurrentVersion(UUID definitionId, @Nullable UUID versionId) {
            Definition definition = this.definitions.get(definitionId);
            if (definition == null) {
                throw new IllegalArgumentException("Unknown EAV schema definition");
            }
            this.definitions.put(
                definitionId,
                new Definition(definition.id(), definition.key(), definition.name(), versionId)
            );
        }
    }

    private record Definition(UUID id, String key, String name, @Nullable UUID currentVersionId) {}
}

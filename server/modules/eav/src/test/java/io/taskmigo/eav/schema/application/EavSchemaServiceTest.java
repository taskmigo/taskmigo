package io.taskmigo.eav.schema.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EavSchemaServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final EavSchemaService service = new EavSchemaServiceImpl(
        this.repository,
        new DirectTransactionRunner(),
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("clones the current published schema into a new draft while preserving logical field identity")
    void shouldClonePublishedSchemaIntoDraft() {
        UUID definitionId = this.service.createDefinition("bug", "Bug");
        UUID severityId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = this.service.createDraft(definitionId);
        v1 = this.service.replaceDraftFields(
            definitionId,
            v1.id(),
            List.of(field(severityId, "severity", "Severity", EavDataType.STRING))
        );
        this.service.publish(definitionId, v1.id());

        EavSchemaVersionInfo v2 = this.service.createDraft(definitionId);

        assertThat(v2.version()).isEqualTo(2);
        assertThat(v2.status()).isEqualTo(EavSchemaStatus.DRAFT);
        assertThat(v2.fields())
            .singleElement()
            .satisfies(field -> {
                assertThat(field.fieldId()).isEqualTo(severityId);
                assertThat(field.key()).isEqualTo("severity");
                assertThat(field.dataType()).isEqualTo(EavDataType.STRING);
            });
    }

    @Test
    @DisplayName("rejects changing key or type while reusing a logical field identity from published history")
    void shouldRejectSemanticMutationOfPublishedLogicalField() {
        UUID definitionId = this.service.createDefinition("bug", "Bug");
        UUID fieldId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = this.service.createDraft(definitionId);
        this.service.replaceDraftFields(
            definitionId,
            v1.id(),
            List.of(field(fieldId, "severity", "Severity", EavDataType.STRING))
        );
        this.service.publish(definitionId, v1.id());
        EavSchemaVersionInfo v2 = this.service.createDraft(definitionId);

        assertThatThrownBy(() ->
            this.service.replaceDraftFields(
                definitionId,
                v2.id(),
                List.of(field(fieldId, "impact", "Impact", EavDataType.STRING))
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            this.service.replaceDraftFields(
                definitionId,
                v2.id(),
                List.of(field(fieldId, "severity", "Severity", EavDataType.ENUM))
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("allows semantic replacement when a new logical field identity is used")
    void shouldAllowSemanticReplacementWithNewLogicalIdentity() {
        UUID definitionId = this.service.createDefinition("bug", "Bug");
        UUID oldId = UUID.randomUUID();
        EavSchemaVersionInfo v1 = this.service.createDraft(definitionId);
        this.service.replaceDraftFields(
            definitionId,
            v1.id(),
            List.of(field(oldId, "severity", "Severity", EavDataType.STRING))
        );
        this.service.publish(definitionId, v1.id());
        EavSchemaVersionInfo v2 = this.service.createDraft(definitionId);
        UUID newId = UUID.randomUUID();

        EavSchemaVersionInfo changed = this.service.replaceDraftFields(
            definitionId,
            v2.id(),
            List.of(field(newId, "severity", "Severity", EavDataType.ENUM))
        );

        assertThat(changed.fields()).singleElement().extracting(EavFieldSpec::fieldId).isEqualTo(newId);
    }

    @Test
    @DisplayName("publishing atomically switches the current version and retiring current clears it")
    void shouldSwitchAndClearCurrentVersion() {
        UUID definitionId = this.service.createDefinition("incident", "Incident");
        EavSchemaVersionInfo v1 = this.service.createDraft(definitionId);
        EavSchemaVersionInfo published1 = this.service.publish(definitionId, v1.id());
        EavSchemaVersionInfo v2 = this.service.createDraft(definitionId);
        EavSchemaVersionInfo published2 = this.service.publish(definitionId, v2.id());

        assertThat(published1.publishedAt()).contains(NOW);
        assertThat(this.service.current(definitionId)).contains(published2);

        this.service.retire(definitionId, published1.id());
        assertThat(this.service.current(definitionId)).contains(published2);

        EavSchemaVersionInfo retired2 = this.service.retire(definitionId, published2.id());
        assertThat(retired2.status()).isEqualTo(EavSchemaStatus.RETIRED);
        assertThat(retired2.retiredAt()).contains(NOW);
        assertThat(this.service.current(definitionId)).isEmpty();
    }

    @Test
    @DisplayName("published and retired schema versions cannot be edited")
    void shouldRejectEditingImmutableVersions() {
        UUID definitionId = this.service.createDefinition("feature", "Feature");
        EavSchemaVersionInfo draft = this.service.createDraft(definitionId);
        EavSchemaVersionInfo published = this.service.publish(definitionId, draft.id());

        assertThatThrownBy(() -> this.service.replaceDraftFields(definitionId, published.id(), List.of())).isInstanceOf(
            IllegalStateException.class
        );

        EavSchemaVersionInfo retired = this.service.retire(definitionId, published.id());
        assertThatThrownBy(() -> this.service.replaceDraftFields(definitionId, retired.id(), List.of())).isInstanceOf(
            IllegalStateException.class
        );
    }

    private static EavFieldSpec field(UUID id, String key, String label, EavDataType type) {
        Map<String, Object> constraints =
            type == EavDataType.ENUM ? Map.of("options", List.of("HIGH", "LOW")) : Map.of();
        return new EavFieldSpec(id, key, label, type, false, constraints);
    }

    private static final class DirectTransactionRunner implements EavTransactionRunner {

        @Override
        public <T> T read(Supplier<T> work) {
            return work.get();
        }

        @Override
        public <T> T write(Supplier<T> work) {
            return work.get();
        }

        @Override
        public void write(Runnable work) {
            work.run();
        }
    }

    private static final class InMemoryRepository implements EavSchemaRepository {

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

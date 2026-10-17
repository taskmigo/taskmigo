package io.taskmigo.ticket.type.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketType;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
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

class TicketTypeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    private final InMemoryTicketTypeRepository ticketTypes = new InMemoryTicketTypeRepository();
    private final FakeEavSchemaService schemas = new FakeEavSchemaService();
    private final TicketTypeService service = new TicketTypeServiceImpl(
        this.ticketTypes,
        this.schemas,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("creates a stable Ticket Type identity and rename preserves its EAV schema ownership")
    void shouldPreserveStableIdentityWhenRenamingTicketType() {
        UUID id = this.service.create("bug", "Bug");
        TicketTypeInfo created = this.service.get(id);

        TicketTypeInfo renamed = this.service.update(id, "Defect", TicketTypeStatus.ACTIVE);

        assertThat(renamed.id()).isEqualTo(id);
        assertThat(renamed.key()).isEqualTo("bug");
        assertThat(renamed.name()).isEqualTo("Defect");
        assertThat(renamed.schemaDefinitionId()).isEqualTo(created.schemaDefinitionId());
        assertThat(this.schemas.definitionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("template revisions clone logical fields and reject the Ticket common namespace")
    void shouldCloneTemplateFieldsAndRejectCommonFieldCollisions() {
        UUID typeId = this.service.create("incident", "Incident");
        EavSchemaVersionInfo draft = this.service.createTemplate(typeId);

        for (String reserved : List.of(
            "id",
            "ticketTypeId",
            "title",
            "status",
            "createdBy",
            "createdAt",
            "updatedAt"
        )) {
            EavFieldSpec collision = field(UUID.randomUUID(), reserved, EavDataType.STRING, false);
            assertThatThrownBy(() -> this.service.replaceTemplate(typeId, draft.id(), List.of(collision)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reserved);
        }

        UUID severityId = UUID.randomUUID();
        EavSchemaVersionInfo replaced = this.service.replaceTemplate(
            typeId,
            draft.id(),
            List.of(field(severityId, "severity", EavDataType.STRING, false))
        );
        EavSchemaVersionInfo published = this.service.publishTemplate(typeId, replaced.id());
        EavSchemaVersionInfo cloned = this.service.createTemplate(typeId);

        assertThat(published.status()).isEqualTo(EavSchemaStatus.PUBLISHED);
        assertThat(cloned.fields())
            .singleElement()
            .satisfies(field -> {
                assertThat(field.fieldId()).isEqualTo(severityId);
                assertThat(field.key()).isEqualTo("severity");
            });
    }

    @Test
    @DisplayName("lists every template version in ascending version order")
    void shouldListTemplateHistory() {
        UUID typeId = this.service.create("history", "History");
        EavSchemaVersionInfo first = this.service.createTemplate(typeId);
        this.service.publishTemplate(typeId, first.id());
        EavSchemaVersionInfo second = this.service.createTemplate(typeId);

        assertThat(this.service.listTemplates(typeId))
            .extracting(EavSchemaVersionInfo::id)
            .containsExactly(first.id(), second.id());
    }

    private static EavFieldSpec field(UUID id, String key, EavDataType type, boolean required) {
        return new EavFieldSpec(id, key, key, type, required, Map.of());
    }

    private static final class InMemoryTicketTypeRepository implements TicketTypeRepository {

        private final Map<UUID, TicketType> values = new LinkedHashMap<>();

        @Override
        public void save(TicketType ticketType) {
            this.values.put(ticketType.id(), ticketType);
        }

        @Override
        public Optional<TicketType> findById(UUID id) {
            return Optional.ofNullable(this.values.get(id));
        }

        @Override
        public boolean existsByKey(String key) {
            return this.values
                .values()
                .stream()
                .anyMatch(type -> type.key().equals(key));
        }

        @Override
        public List<TicketType> findAll() {
            return List.copyOf(this.values.values());
        }
    }

    private static final class FakeEavSchemaService implements EavSchemaService {

        private final Map<UUID, Definition> definitions = new LinkedHashMap<>();
        private final Map<UUID, EavSchemaVersionInfo> versions = new LinkedHashMap<>();

        int definitionCount() {
            return this.definitions.size();
        }

        @Override
        public UUID createDefinition(String key, String name) {
            UUID id = UUID.randomUUID();
            this.definitions.put(id, new Definition(id, key, name, null));
            return id;
        }

        @Override
        public EavSchemaVersionInfo createDraft(UUID definitionId) {
            Definition definition = requireDefinition(definitionId);
            UUID currentVersionId = definition.currentVersionId();
            List<EavFieldSpec> fields =
                currentVersionId == null
                    ? List.of()
                    : Objects.requireNonNull(this.versions.get(currentVersionId)).fields();
            long version =
                this.versions
                    .values()
                    .stream()
                    .filter(v -> v.definitionId().equals(definitionId))
                    .count() + 1;
            EavSchemaVersionInfo draft = new EavSchemaVersionInfo(
                UUID.randomUUID(),
                definitionId,
                version,
                EavSchemaStatus.DRAFT,
                fields,
                Optional.empty(),
                Optional.empty()
            );
            this.versions.put(draft.id(), draft);
            return draft;
        }

        @Override
        public EavSchemaVersionInfo replaceDraftFields(UUID definitionId, UUID versionId, List<EavFieldSpec> fields) {
            EavSchemaVersionInfo existing = version(definitionId, versionId);
            if (existing.status() != EavSchemaStatus.DRAFT) {
                throw new IllegalStateException("Only a draft can be edited");
            }
            EavSchemaVersionInfo changed = new EavSchemaVersionInfo(
                existing.id(),
                existing.definitionId(),
                existing.version(),
                existing.status(),
                fields,
                existing.publishedAt(),
                existing.retiredAt()
            );
            this.versions.put(versionId, changed);
            return changed;
        }

        @Override
        public EavSchemaVersionInfo publish(UUID definitionId, UUID versionId) {
            EavSchemaVersionInfo existing = version(definitionId, versionId);
            EavSchemaVersionInfo published = new EavSchemaVersionInfo(
                existing.id(),
                existing.definitionId(),
                existing.version(),
                EavSchemaStatus.PUBLISHED,
                existing.fields(),
                Optional.of(NOW),
                Optional.empty()
            );
            this.versions.put(versionId, published);
            Definition definition = requireDefinition(definitionId);
            this.definitions.put(
                definitionId,
                new Definition(definition.id(), definition.key(), definition.name(), versionId)
            );
            return published;
        }

        @Override
        public EavSchemaVersionInfo retire(UUID definitionId, UUID versionId) {
            EavSchemaVersionInfo existing = version(definitionId, versionId);
            EavSchemaVersionInfo retired = new EavSchemaVersionInfo(
                existing.id(),
                existing.definitionId(),
                existing.version(),
                EavSchemaStatus.RETIRED,
                existing.fields(),
                existing.publishedAt(),
                Optional.of(NOW)
            );
            this.versions.put(versionId, retired);
            Definition definition = requireDefinition(definitionId);
            UUID current = versionId.equals(definition.currentVersionId()) ? null : definition.currentVersionId();
            this.definitions.put(
                definitionId,
                new Definition(definition.id(), definition.key(), definition.name(), current)
            );
            return retired;
        }

        @Override
        public Optional<EavSchemaVersionInfo> current(UUID definitionId) {
            Definition definition = requireDefinition(definitionId);
            return Optional.ofNullable(definition.currentVersionId()).map(this.versions::get);
        }

        @Override
        public List<EavSchemaVersionInfo> listVersions(UUID definitionId) {
            requireDefinition(definitionId);
            return this.versions
                .values()
                .stream()
                .filter(version -> version.definitionId().equals(definitionId))
                .sorted(Comparator.comparingLong(EavSchemaVersionInfo::version))
                .toList();
        }

        @Override
        public EavSchemaVersionInfo version(UUID definitionId, UUID versionId) {
            EavSchemaVersionInfo version = this.versions.get(versionId);
            if (version == null || !version.definitionId().equals(definitionId)) {
                throw new IllegalArgumentException("Unknown EAV schema version");
            }
            return version;
        }

        private Definition requireDefinition(UUID id) {
            Definition definition = this.definitions.get(id);
            if (definition == null) {
                throw new IllegalArgumentException("Unknown EAV schema definition");
            }
            return definition;
        }
    }

    private record Definition(UUID id, String key, String name, @Nullable UUID currentVersionId) {}
}

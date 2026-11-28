package io.taskmigo.ticket.type.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import io.taskmigo.ticket.field.application.TicketFieldServiceImpl;
import io.taskmigo.ticket.field.application.api.TicketFieldException;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTemplateStatus;
import io.taskmigo.ticket.type.application.api.TicketTypeException;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketType;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
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
    private final InMemoryFieldRegistry registry = new InMemoryFieldRegistry();
    private final TicketFieldService fieldService = new TicketFieldServiceImpl(this.registry);
    private final TicketTypeService service = new TicketTypeServiceImpl(
        this.ticketTypes,
        this.schemas,
        this.registry,
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("creates a stable Ticket Type identity and rename preserves its EAV schema ownership")
    void shouldPreserveStableIdentityWhenRenamingTicketType() {
        UUID id = this.service.create("bug", "Bug", List.of()).id();
        UUID schemaDefinitionId = this.ticketTypes.findById(id).orElseThrow().schemaDefinitionId();

        TicketTypeInfo renamed = this.service.update(id, "Defect", TicketTypeStatus.ACTIVE);

        assertThat(renamed.id()).isEqualTo(id);
        assertThat(renamed.key()).isEqualTo("bug");
        assertThat(renamed.name()).isEqualTo("Defect");
        assertThat(this.ticketTypes.findById(id).orElseThrow().schemaDefinitionId()).isEqualTo(schemaDefinitionId);
        assertThat(this.schemas.definitionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("creates the initial active template and replaces it with an immutable revision")
    void shouldActivateTemplateSnapshots() {
        UUID severityId = this.fieldService.create("severity", "Severity", TicketFieldType.STRING).id();
        TicketFieldSpec field = new TicketFieldSpec(severityId, null, false, Map.of());
        TicketTypeInfo type = this.service.create("incident", "Incident", List.of(field));
        TicketTemplateInfo first = type.currentTemplate();
        TicketTemplateInfo second = this.service.createTemplate(type.id(), first.id(), List.of(field));

        assertThat(first.status()).isEqualTo(TicketTemplateStatus.ACTIVE);
        assertThat(second.status()).isEqualTo(TicketTemplateStatus.ACTIVE);
        assertThat(this.service.getTemplate(type.id(), first.id()).status()).isEqualTo(TicketTemplateStatus.SUPERSEDED);
        assertThat(this.service.get(type.id()).currentTemplate().id()).isEqualTo(second.id());
        assertThat(second.fields())
            .singleElement()
            .satisfies(value -> {
                assertThat(value.fieldId()).isEqualTo(severityId);
                assertThat(value.key()).isEqualTo("severity");
                assertThat(value.dataType()).isEqualTo(TicketFieldType.STRING);
            });
        assertThatThrownBy(() ->
            this.service.createTemplate(type.id(), first.id(), List.of(field))
        ).isInstanceOfSatisfying(TicketTypeException.class, exception ->
            assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT)
        );
    }

    @Test
    @DisplayName("registration rejects reserved keys and conflicting types")
    void shouldRejectConflictingFieldRegistration() {
        for (String reserved : List.of(
            "id",
            "ticketTypeId",
            "title",
            "status",
            "createdBy",
            "createdAt",
            "updatedAt"
        )) {
            assertThatThrownBy(() -> this.fieldService.create(reserved, reserved, TicketFieldType.STRING)).isInstanceOf(
                TicketFieldException.class
            );
        }

        this.fieldService.create("severity", "Severity", TicketFieldType.STRING);
        assertThatThrownBy(() -> this.fieldService.create("severity", "Severity", TicketFieldType.NUMBER)).isInstanceOf(
            TicketFieldException.class
        );
    }

    @Test
    @DisplayName("archived fields remain in historical snapshots but cannot be attached to a new revision")
    void shouldPreserveHistoryWhenFieldIsArchived() {
        UUID fieldId = this.fieldService.create("severity", "Severity", TicketFieldType.STRING).id();
        TicketFieldSpec spec = new TicketFieldSpec(fieldId, null, false, Map.of());
        TicketTypeInfo type = this.service.create("incident", "Incident", List.of(spec));
        UUID previous = type.currentTemplate().id();
        this.fieldService.update(fieldId, null, TicketFieldStatus.ARCHIVED);

        assertThatThrownBy(() ->
            this.service.createTemplate(type.id(), previous, List.of(spec))
        ).isInstanceOfSatisfying(TicketFieldException.class, exception ->
            assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT)
        );
        assertThatThrownBy(() ->
            this.service.createTemplate(
                type.id(),
                previous,
                List.of(new TicketFieldSpec(UUID.randomUUID(), null, false, Map.of()))
            )
        ).isInstanceOf(TicketFieldException.class);
        TicketTemplateInfo next = this.service.createTemplate(type.id(), previous, List.of());

        assertThat(this.service.getTemplate(type.id(), previous).fields()).hasSize(1);
        assertThat(next.fields()).isEmpty();
        assertThat(this.service.listTemplates(type.id()))
            .extracting(TicketTemplateInfo::id)
            .containsExactly(previous, next.id());
    }

    private static final class InMemoryFieldRegistry implements TicketFieldRegistry {

        private final Map<UUID, Entry> fields = new LinkedHashMap<>();

        @Override
        public Optional<Entry> findByKey(String key) {
            return this.fields
                .values()
                .stream()
                .filter(entry -> entry.key().equals(key))
                .findFirst();
        }

        @Override
        public Optional<Entry> findById(UUID id) {
            return Optional.ofNullable(this.fields.get(id));
        }

        @Override
        public List<Entry> findAll() {
            return List.copyOf(this.fields.values());
        }

        @Override
        public Optional<Entry> lockById(UUID id) {
            return findById(id);
        }

        @Override
        public void insert(Entry entry) {
            if (findByKey(entry.key()).isPresent() || this.fields.containsKey(entry.id())) {
                throw new IllegalArgumentException("Duplicate field");
            }
            this.fields.put(entry.id(), entry);
        }

        @Override
        public void update(Entry entry) {
            this.fields.put(entry.id(), entry);
        }
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
        public Optional<TicketType> lockById(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<TicketType> lockForCreation(UUID id) {
            return findById(id);
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
        public List<EavSchemaVersionInfo> currentVersions(Collection<UUID> definitionIds) {
            return definitionIds.stream().map(this::current).flatMap(Optional::stream).toList();
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

package io.taskmigo.ticket.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.value.application.EavInstanceService;
import io.taskmigo.eav.value.application.EavInstanceView;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.ListQueryCompiler;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketPatch;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.domain.Ticket;
import io.taskmigo.ticket.support.TestTicketAuthorization;
import io.taskmigo.ticket.support.TicketQueryTestSupport;
import io.taskmigo.ticket.type.application.TicketTypeRepository;
import io.taskmigo.ticket.type.domain.TicketType;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
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

class TicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");
    private static final ObjectAuthorizationPredicate<TicketInfo> ALLOW_ALL = new ObjectAuthorizationPredicate<>() {
        @Override
        public boolean isAlwaysTrue() {
            return true;
        }

        @Override
        public boolean isAlwaysFalse() {
            return false;
        }
    };

    private final InMemoryTicketTypeRepository ticketTypes = new InMemoryTicketTypeRepository();
    private final FakeEavSchemaService schemas = new FakeEavSchemaService();
    private final FakeEavInstanceService instances = new FakeEavInstanceService(this.schemas);
    private final InMemoryTicketRepository tickets = new InMemoryTicketRepository();
    private final TicketService service = new TicketServiceImpl(
        this.ticketTypes,
        this.schemas,
        this.instances,
        this.tickets,
        this.tickets,
        TicketQueryTestSupport.emptyContexts(),
        TicketQueryTestSupport.schemas("tickets.list", "tickets.read", "tickets.update"),
        new ListQueryCompiler(),
        Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("creates a Ticket pinned to the current published template with actor provenance")
    void shouldCreateTicketPinnedToCurrentTemplate() {
        UUID definitionId = this.schemas.createDefinition("ticket-type:test", "Test");
        UUID typeId = UUID.randomUUID();
        this.ticketTypes.save(TicketType.active(typeId, "test", "Test", definitionId, NOW));
        EavSchemaVersionInfo v1 = this.schemas.publishWithFields(
            definitionId,
            this.schemas.createDraft(definitionId).id(),
            List.of(field(UUID.randomUUID(), "severity", false))
        );
        UUID actorId = UUID.randomUUID();

        UUID ticketId = this.service.create(typeId, "Broken login", "OPEN", Map.of("severity", "HIGH"), actorId);

        Ticket stored = this.tickets.findById(ticketId).orElseThrow();
        EavInstanceView instance = this.instances.get(stored.schemaInstanceId());
        assertThat(stored.ticketTypeId()).isEqualTo(typeId);
        assertThat(stored.createdBy()).isEqualTo(actorId);
        assertThat(stored.createdAt()).isEqualTo(NOW);
        assertThat(stored.updatedAt()).isEqualTo(NOW);
        assertThat(instance.schemaVersionId()).isEqualTo(v1.id());
        assertThat(instance.values()).containsEntry("severity", "HIGH");
    }

    @Test
    @DisplayName("creation rejects retired types and blank common values")
    void shouldRejectIneligibleTicketCreation() {
        UUID definitionId = this.schemas.createDefinition("ticket-type:retired", "Retired");
        UUID activeId = UUID.randomUUID();
        UUID retiredId = UUID.randomUUID();
        this.ticketTypes.save(TicketType.active(activeId, "active", "Active", definitionId, NOW));
        this.ticketTypes.save(
            TicketType.active(retiredId, "retired", "Retired", definitionId, NOW).update(
                "Retired",
                TicketTypeStatus.RETIRED,
                NOW
            )
        );
        EavSchemaVersionInfo draft = this.schemas.createDraft(definitionId);
        this.schemas.publishWithFields(definitionId, draft.id(), List.of());

        assertThatThrownBy(() ->
            this.service.create(retiredId, "No", "OPEN", Map.of(), UUID.randomUUID())
        ).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> this.service.create(activeId, " ", "OPEN", Map.of(), UUID.randomUUID())).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> this.service.create(activeId, "Valid", " ", Map.of(), UUID.randomUUID())).isInstanceOf(
            IllegalArgumentException.class
        );
    }

    @Test
    @DisplayName("updates against the pinned historical template and applies custom PATCH null semantics")
    void shouldUpdateAgainstPinnedHistoricalTemplate() {
        UUID definitionId = this.schemas.createDefinition("ticket-type:history", "History");
        UUID typeId = UUID.randomUUID();
        this.ticketTypes.save(TicketType.active(typeId, "history", "History", definitionId, NOW));
        EavSchemaVersionInfo v1 = this.schemas.publishWithFields(
            definitionId,
            this.schemas.createDraft(definitionId).id(),
            List.of(field(UUID.randomUUID(), "summary", true), field(UUID.randomUUID(), "note", false))
        );
        UUID ticketId = this.service.create(
            typeId,
            "Legacy",
            "OPEN",
            Map.of("summary", "v1", "note", "remove me"),
            UUID.randomUUID()
        );

        EavSchemaVersionInfo v2Draft = this.schemas.createDraft(definitionId);
        this.schemas.publishWithFields(
            definitionId,
            v2Draft.id(),
            List.of(field(UUID.randomUUID(), "summaryV2", true))
        );

        TicketInfo updated = this.service.update(
            ticketId,
            new TicketPatch("Legacy renamed", null, nullableMap("summary", "still v1", "note", null)),
            TestTicketAuthorization.context(ALLOW_ALL)
        );

        assertThat(updated.templateVersionId()).isEqualTo(v1.id());
        assertThat(updated.title()).isEqualTo("Legacy renamed");
        assertThat(updated.status()).isEqualTo("OPEN");
        assertThat(updated.customFields()).containsEntry("summary", "still v1").doesNotContainKey("note");

        assertThatThrownBy(() ->
            this.service.update(
                ticketId,
                new TicketPatch(null, null, nullableMap("summary", null)),
                TestTicketAuthorization.context(ALLOW_ALL)
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }

    private static EavFieldSpec field(UUID id, String key, boolean required) {
        return new EavFieldSpec(id, key, key, EavDataType.STRING, required, Map.of());
    }

    private static Map<String, @Nullable Object> nullableMap(@Nullable Object... entries) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            String key = (String) Objects.requireNonNull(entries[index]);
            values.put(key, entries[index + 1]);
        }
        return Collections.unmodifiableMap(values);
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

    private static final class InMemoryTicketRepository implements TicketRepository, TicketQueryRepository {

        private final Map<UUID, Ticket> values = new LinkedHashMap<>();

        @Override
        public void save(Ticket ticket) {
            this.values.put(ticket.id(), ticket);
        }

        @Override
        public Optional<Ticket> findById(UUID id) {
            return Optional.ofNullable(this.values.get(id));
        }

        @Override
        public Optional<Ticket> findAuthorized(
            UUID id,
            ObjectAuthorizationPredicate<TicketInfo> authorization,
            QueryFieldContext context
        ) {
            if (authorization.isAlwaysFalse()) {
                return Optional.empty();
            }
            return findById(id);
        }

        @Override
        public OffsetPage<Ticket> list(
            int page,
            int perPage,
            @Nullable UUID ticketTypeId,
            ListQuerySpec<TicketInfo> query,
            ObjectAuthorizationPredicate<TicketInfo> authorization
        ) {
            if (authorization.isAlwaysFalse()) {
                return new OffsetPage<>(List.of(), 0, 0);
            }
            List<Ticket> matching = this.values
                .values()
                .stream()
                .filter(ticket -> ticketTypeId == null || ticket.ticketTypeId().equals(ticketTypeId))
                .sorted(Comparator.comparing(Ticket::id))
                .toList();
            int from = Math.min((page - 1) * perPage, matching.size());
            int to = Math.min(from + perPage, matching.size());
            long totalPages = matching.isEmpty() ? 0 : (matching.size() + perPage - 1L) / perPage;
            return new OffsetPage<>(matching.subList(from, to), matching.size(), totalPages);
        }
    }

    private static final class FakeEavSchemaService implements EavSchemaService {

        private final Map<UUID, Definition> definitions = new LinkedHashMap<>();
        private final Map<UUID, EavSchemaVersionInfo> versions = new LinkedHashMap<>();

        @Override
        public UUID createDefinition(String key, String name) {
            UUID id = UUID.randomUUID();
            this.definitions.put(id, new Definition(id, null));
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
            EavSchemaVersionInfo draft = new EavSchemaVersionInfo(
                UUID.randomUUID(),
                definitionId,
                this.versions
                    .values()
                    .stream()
                    .filter(v -> v.definitionId().equals(definitionId))
                    .count() + 1,
                EavSchemaStatus.DRAFT,
                fields,
                Optional.empty(),
                Optional.empty()
            );
            this.versions.put(draft.id(), draft);
            return draft;
        }

        EavSchemaVersionInfo publishWithFields(UUID definitionId, UUID versionId, List<EavFieldSpec> fields) {
            replaceDraftFields(definitionId, versionId, fields);
            return publish(definitionId, versionId);
        }

        @Override
        public EavSchemaVersionInfo replaceDraftFields(UUID definitionId, UUID versionId, List<EavFieldSpec> fields) {
            EavSchemaVersionInfo existing = version(definitionId, versionId);
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
            this.definitions.put(definitionId, new Definition(definitionId, versionId));
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
            this.definitions.put(
                definitionId,
                new Definition(
                    definitionId,
                    versionId.equals(definition.currentVersionId()) ? null : definition.currentVersionId()
                )
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

    private static final class FakeEavInstanceService implements EavInstanceService {

        private final FakeEavSchemaService schemas;
        private final Map<UUID, EavInstanceView> values = new LinkedHashMap<>();

        FakeEavInstanceService(FakeEavSchemaService schemas) {
            this.schemas = schemas;
        }

        @Override
        public EavInstanceView create(UUID definitionId, UUID versionId, Map<String, ?> supplied) {
            EavSchemaVersionInfo version = this.schemas.version(definitionId, versionId);
            Map<String, Object> normalized = validate(version, supplied, Map.of(), true);
            EavInstanceView view = new EavInstanceView(UUID.randomUUID(), definitionId, versionId, normalized);
            this.values.put(view.id(), view);
            return view;
        }

        @Override
        public EavInstanceView get(UUID instanceId) {
            EavInstanceView view = this.values.get(instanceId);
            if (view == null) {
                throw new IllegalArgumentException("Unknown EAV instance");
            }
            return view;
        }

        @Override
        public EavInstanceView patch(UUID instanceId, Map<String, ?> changes) {
            EavInstanceView current = get(instanceId);
            EavSchemaVersionInfo version = this.schemas.version(
                current.schemaDefinitionId(),
                current.schemaVersionId()
            );
            Map<String, Object> normalized = validate(version, changes, current.values(), false);
            EavInstanceView changed = new EavInstanceView(
                current.id(),
                current.schemaDefinitionId(),
                current.schemaVersionId(),
                normalized
            );
            this.values.put(instanceId, changed);
            return changed;
        }

        private static Map<String, Object> validate(
            EavSchemaVersionInfo version,
            Map<String, ?> changes,
            Map<String, Object> current,
            boolean creating
        ) {
            Map<String, EavFieldSpec> fields = new LinkedHashMap<>();
            version.fields().forEach(field -> fields.put(field.key(), field));
            for (String key : changes.keySet()) {
                if (!fields.containsKey(key)) {
                    throw new IllegalArgumentException("Unknown EAV field key '" + key + "'");
                }
            }
            Map<String, Object> result = new LinkedHashMap<>(current);
            for (EavFieldSpec field : version.fields()) {
                if (!changes.containsKey(field.key())) {
                    if (creating && field.required()) {
                        throw new IllegalArgumentException("Missing required EAV field '" + field.key() + "'");
                    }
                    continue;
                }
                Object value = changes.get(field.key());
                if (value == null) {
                    if (field.required()) {
                        throw new IllegalArgumentException("Missing required EAV field '" + field.key() + "'");
                    }
                    result.remove(field.key());
                } else {
                    result.put(field.key(), value);
                }
            }
            return result;
        }
    }

    private record Definition(UUID id, @Nullable UUID currentVersionId) {}
}

package io.taskmigo.ticket.type.application;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketType;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates Ticket Type metadata with a dedicated generic EAV schema definition.
@Transactional
public class TicketTypeServiceImpl implements TicketTypeService {

    private static final Set<String> COMMON_FIELD_KEYS = Set.of(
        "id",
        "ticketTypeId",
        "schemaInstanceId",
        "title",
        "status",
        "createdBy",
        "createdAt",
        "updatedAt",
        "version"
    );

    private final TicketTypeRepository ticketTypes;
    private final EavSchemaService schemas;
    private final Clock clock;

    public TicketTypeServiceImpl(TicketTypeRepository ticketTypes, EavSchemaService schemas, Clock clock) {
        this.ticketTypes = Objects.requireNonNull(ticketTypes);
        this.schemas = Objects.requireNonNull(schemas);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public UUID create(String key, String name) {
        String normalizedKey = requireNonBlank(key, "Ticket Type key");
        String normalizedName = requireNonBlank(name, "Ticket Type name");
        if (this.ticketTypes.existsByKey(normalizedKey)) {
            throw new IllegalArgumentException("Ticket Type key already exists: " + normalizedKey);
        }

        UUID id = UUID.randomUUID();
        UUID schemaDefinitionId = this.schemas.createDefinition("ticket-type:" + id, normalizedName);
        this.ticketTypes.save(TicketType.active(id, normalizedKey, normalizedName, schemaDefinitionId, this.now()));
        return id;
    }

    @Transactional(readOnly = true)
    @Override
    public TicketTypeInfo get(UUID id) {
        return toInfo(this.requireType(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<TicketTypeInfo> list() {
        return this.ticketTypes
            .findAll()
            .stream()
            .sorted(Comparator.comparing(TicketType::key))
            .map(TicketTypeServiceImpl::toInfo)
            .toList();
    }

    @Override
    public TicketTypeInfo update(UUID id, String name, TicketTypeStatus status) {
        Objects.requireNonNull(status);
        TicketType updated = this.requireType(id).update(requireNonBlank(name, "Ticket Type name"), status, this.now());
        this.ticketTypes.save(updated);
        return toInfo(updated);
    }

    @Override
    public EavSchemaVersionInfo createTemplate(UUID ticketTypeId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas.createDraft(type.schemaDefinitionId());
    }

    @Override
    public EavSchemaVersionInfo replaceTemplate(UUID ticketTypeId, UUID versionId, List<EavFieldSpec> fields) {
        List<EavFieldSpec> replacement = List.copyOf(Objects.requireNonNull(fields));
        TicketType type = this.requireType(ticketTypeId);
        rejectCommonFieldCollisions(replacement);
        return this.schemas.replaceDraftFields(
            type.schemaDefinitionId(),
            Objects.requireNonNull(versionId),
            replacement
        );
    }

    @Override
    public EavSchemaVersionInfo publishTemplate(UUID ticketTypeId, UUID versionId) {
        TicketType type = this.requireType(ticketTypeId);
        EavSchemaVersionInfo published = this.schemas.publish(
            type.schemaDefinitionId(),
            Objects.requireNonNull(versionId)
        );
        rejectCommonFieldCollisions(published.fields());
        return published;
    }

    @Override
    public EavSchemaVersionInfo retireTemplate(UUID ticketTypeId, UUID versionId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas.retire(type.schemaDefinitionId(), Objects.requireNonNull(versionId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<EavSchemaVersionInfo> listTemplates(UUID ticketTypeId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas.listVersions(type.schemaDefinitionId());
    }

    @Transactional(readOnly = true)
    @Override
    public EavSchemaVersionInfo getTemplate(UUID ticketTypeId, UUID versionId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas.version(type.schemaDefinitionId(), Objects.requireNonNull(versionId));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<EavSchemaVersionInfo> currentTemplate(UUID ticketTypeId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas.current(type.schemaDefinitionId());
    }

    private TicketType requireType(UUID id) {
        Objects.requireNonNull(id);
        return this.ticketTypes.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown Ticket Type"));
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void rejectCommonFieldCollisions(List<EavFieldSpec> fields) {
        for (EavFieldSpec field : fields) {
            if (COMMON_FIELD_KEYS.contains(field.key())) {
                throw new IllegalArgumentException(
                    "Custom field key collides with Ticket common field '" + field.key() + "'"
                );
            }
        }
    }

    private static TicketTypeInfo toInfo(TicketType type) {
        return new TicketTypeInfo(
            type.id(),
            type.key(),
            type.name(),
            type.status(),
            type.schemaDefinitionId(),
            type.createdAt(),
            type.updatedAt()
        );
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}

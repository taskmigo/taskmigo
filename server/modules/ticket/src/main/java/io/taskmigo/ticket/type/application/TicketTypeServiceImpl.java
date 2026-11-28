package io.taskmigo.ticket.type.application;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.ticket.field.application.TicketFieldMappings;
import io.taskmigo.ticket.field.application.TicketFieldRegistry;
import io.taskmigo.ticket.field.application.api.TicketFieldException;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.type.application.api.TicketFieldInfo;
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
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates Ticket Type metadata with a dedicated generic EAV schema definition.
@Transactional
public class TicketTypeServiceImpl implements TicketTypeService {

    private final TicketTypeRepository ticketTypes;
    private final EavSchemaService schemas;
    private final TicketFieldRegistry fields;
    private final Clock clock;

    public TicketTypeServiceImpl(
        TicketTypeRepository ticketTypes,
        EavSchemaService schemas,
        TicketFieldRegistry fields,
        Clock clock
    ) {
        this.ticketTypes = Objects.requireNonNull(ticketTypes);
        this.schemas = Objects.requireNonNull(schemas);
        this.fields = Objects.requireNonNull(fields);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public TicketTypeInfo create(String key, String name, List<TicketFieldSpec> fields) {
        String normalizedKey = requireNonBlank(key, "Ticket Type key");
        String normalizedName = requireNonBlank(name, "Ticket Type name");
        if (this.ticketTypes.existsByKey(normalizedKey)) {
            throw new TicketTypeException(
                DomainFailureType.CONFLICT,
                "Ticket Type key already exists: " + normalizedKey
            );
        }

        UUID id = UUID.randomUUID();
        UUID schemaDefinitionId = this.schemas.createDefinition("ticket-type:" + id, normalizedName);
        TicketType type = TicketType.active(id, normalizedKey, normalizedName, schemaDefinitionId, this.now());
        this.activate(type, fields);
        this.ticketTypes.save(type);
        return this.toInfo(type);
    }

    @Transactional(readOnly = true)
    @Override
    public TicketTypeInfo get(UUID id) {
        return this.toInfo(this.requireType(id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<TicketTypeInfo> list() {
        List<TicketType> types = this.ticketTypes.findAll();
        Map<UUID, EavSchemaVersionInfo> current = this.schemas
            .currentVersions(types.stream().map(TicketType::schemaDefinitionId).toList())
            .stream()
            .collect(Collectors.toMap(EavSchemaVersionInfo::definitionId, Function.identity()));
        return types
            .stream()
            .sorted(Comparator.comparing(TicketType::key))
            .map(type -> {
                EavSchemaVersionInfo template = current.get(type.schemaDefinitionId());
                if (template == null) {
                    throw new IllegalStateException("Ticket Type has no active template");
                }
                return toInfo(type, template);
            })
            .toList();
    }

    @Override
    public TicketTypeInfo update(UUID id, @Nullable String name, @Nullable TicketTypeStatus status) {
        TicketType current = this.lockType(id);
        TicketType updated = current.update(
            name == null ? current.name() : requireNonBlank(name, "Ticket Type name"),
            status == null ? current.status() : status,
            this.now()
        );
        this.ticketTypes.save(updated);
        return this.toInfo(updated);
    }

    @Override
    public TicketTemplateInfo createTemplate(
        UUID ticketTypeId,
        UUID expectedTemplateVersionId,
        List<TicketFieldSpec> fields
    ) {
        TicketType type = this.lockType(ticketTypeId);
        EavSchemaVersionInfo current = this.requireCurrent(type);
        if (!current.id().equals(expectedTemplateVersionId)) {
            throw new TicketTypeException(
                DomainFailureType.CONFLICT,
                "Ticket template has changed; reload the current schema"
            );
        }
        return this.activate(type, fields);
    }

    private TicketTemplateInfo activate(TicketType type, List<TicketFieldSpec> fields) {
        List<EavFieldSpec> resolved = List.copyOf(Objects.requireNonNull(fields))
            .stream()
            .sorted(Comparator.comparing(TicketFieldSpec::fieldId))
            .map(this::toEavField)
            .toList();
        var previous = this.schemas.current(type.schemaDefinitionId());
        EavSchemaVersionInfo draft = this.schemas.createDraft(type.schemaDefinitionId());
        this.schemas.replaceDraftFields(type.schemaDefinitionId(), draft.id(), resolved);
        EavSchemaVersionInfo activated = this.schemas.publish(type.schemaDefinitionId(), draft.id());
        previous.ifPresent(version -> this.schemas.retire(type.schemaDefinitionId(), version.id()));
        return toTemplateInfo(activated);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TicketTemplateInfo> listTemplates(UUID ticketTypeId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas
            .listVersions(type.schemaDefinitionId())
            .stream()
            .map(TicketTypeServiceImpl::toTemplateInfo)
            .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public TicketTemplateInfo getTemplate(UUID ticketTypeId, UUID versionId) {
        TicketType type = this.requireType(ticketTypeId);
        return toTemplateInfo(this.schemas.version(type.schemaDefinitionId(), Objects.requireNonNull(versionId)));
    }

    private EavSchemaVersionInfo requireCurrent(TicketType type) {
        return this.schemas
            .current(type.schemaDefinitionId())
            .orElseThrow(() -> new IllegalStateException("Ticket Type has no active template"));
    }

    private TicketType lockType(UUID id) {
        return this.ticketTypes
            .lockById(id)
            .orElseThrow(() -> new TicketTypeException(DomainFailureType.NOT_FOUND, "Unknown Ticket Type"));
    }

    private TicketType requireType(UUID id) {
        Objects.requireNonNull(id);
        return this.ticketTypes
            .findById(id)
            .orElseThrow(() -> new TicketTypeException(DomainFailureType.NOT_FOUND, "Unknown Ticket Type"));
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private EavFieldSpec toEavField(TicketFieldSpec field) {
        TicketFieldRegistry.Entry entry = this.requireActiveField(field.fieldId());
        return new EavFieldSpec(
            entry.id(),
            entry.key(),
            field.label() == null ? entry.label() : field.label(),
            TicketFieldMappings.toEavType(entry.dataType()),
            field.required(),
            field.constraints()
        );
    }

    private TicketFieldRegistry.Entry requireActiveField(UUID fieldId) {
        TicketFieldRegistry.Entry entry = this.fields
            .lockById(fieldId)
            .orElseThrow(() ->
                new TicketFieldException(DomainFailureType.NOT_FOUND, "Unknown Ticket field: " + fieldId)
            );
        if (entry.status() != TicketFieldStatus.ACTIVE) {
            throw new TicketFieldException(
                DomainFailureType.CONFLICT,
                "Archived Ticket field cannot be attached or published: " + entry.key()
            );
        }
        return entry;
    }

    private TicketTypeInfo toInfo(TicketType type) {
        return toInfo(type, this.requireCurrent(type));
    }

    private static TicketTypeInfo toInfo(TicketType type, EavSchemaVersionInfo template) {
        return new TicketTypeInfo(
            type.id(),
            type.key(),
            type.name(),
            type.status(),
            type.createdAt(),
            type.updatedAt(),
            toTemplateInfo(template)
        );
    }

    private static TicketTemplateInfo toTemplateInfo(EavSchemaVersionInfo template) {
        return new TicketTemplateInfo(
            template.id(),
            template.version(),
            toTemplateStatus(template.status()),
            template.fields().stream().map(TicketTypeServiceImpl::toFieldInfo).toList(),
            template.publishedAt().orElseThrow(),
            template.retiredAt()
        );
    }

    private static TicketFieldInfo toFieldInfo(EavFieldSpec field) {
        return new TicketFieldInfo(
            field.fieldId(),
            field.key(),
            field.label(),
            TicketFieldMappings.fromEavType(field.dataType()),
            field.required(),
            field.constraints()
        );
    }

    private static TicketTemplateStatus toTemplateStatus(EavSchemaStatus status) {
        return switch (status) {
            case DRAFT -> throw new IllegalStateException("Draft template is not a public resource");
            case PUBLISHED -> TicketTemplateStatus.ACTIVE;
            case RETIRED -> TicketTemplateStatus.SUPERSEDED;
        };
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}

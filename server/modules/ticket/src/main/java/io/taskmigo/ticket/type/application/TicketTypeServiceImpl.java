package io.taskmigo.ticket.type.application;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.ticket.type.application.api.TicketFieldInfo;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTemplateStatus;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketType;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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
    public TicketTemplateInfo createTemplate(UUID ticketTypeId) {
        TicketType type = this.requireType(ticketTypeId);
        return toTemplateInfo(this.schemas.createDraft(type.schemaDefinitionId()));
    }

    @Override
    public TicketTemplateInfo replaceTemplate(UUID ticketTypeId, UUID versionId, List<TicketFieldSpec> fields) {
        TicketType type = this.requireType(ticketTypeId);
        UUID templateVersionId = Objects.requireNonNull(versionId);
        EavSchemaVersionInfo current = this.schemas.version(type.schemaDefinitionId(), templateVersionId);
        List<TicketFieldSpec> replacement = List.copyOf(Objects.requireNonNull(fields));
        rejectCommonFieldCollisions(replacement);
        List<EavFieldSpec> resolved = resolveFields(replacement, current.fields());
        return toTemplateInfo(this.schemas.replaceDraftFields(type.schemaDefinitionId(), templateVersionId, resolved));
    }

    @Override
    public TicketTemplateInfo publishTemplate(UUID ticketTypeId, UUID versionId) {
        TicketType type = this.requireType(ticketTypeId);
        EavSchemaVersionInfo published = this.schemas.publish(
            type.schemaDefinitionId(),
            Objects.requireNonNull(versionId)
        );
        rejectEavCommonFieldCollisions(published.fields());
        return toTemplateInfo(published);
    }

    @Override
    public TicketTemplateInfo retireTemplate(UUID ticketTypeId, UUID versionId) {
        TicketType type = this.requireType(ticketTypeId);
        return toTemplateInfo(this.schemas.retire(type.schemaDefinitionId(), Objects.requireNonNull(versionId)));
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

    @Transactional(readOnly = true)
    @Override
    public Optional<TicketTemplateInfo> currentTemplate(UUID ticketTypeId) {
        TicketType type = this.requireType(ticketTypeId);
        return this.schemas.current(type.schemaDefinitionId()).map(TicketTypeServiceImpl::toTemplateInfo);
    }

    private TicketType requireType(UUID id) {
        Objects.requireNonNull(id);
        return this.ticketTypes.findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown Ticket Type"));
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static List<EavFieldSpec> resolveFields(List<TicketFieldSpec> fields, List<EavFieldSpec> existingFields) {
        Map<String, EavFieldSpec> existing = new LinkedHashMap<>();
        existingFields.forEach(field -> existing.put(field.key(), field));
        return fields
            .stream()
            .map(field -> toEavField(field, existing.get(field.key())))
            .toList();
    }

    private static EavFieldSpec toEavField(TicketFieldSpec field, @Nullable EavFieldSpec previous) {
        EavDataType dataType = toEavDataType(field.dataType());
        UUID identity = field.fieldId();
        if (identity == null && previous != null && previous.dataType() == dataType) {
            identity = previous.fieldId();
        }
        if (identity == null) {
            identity = UUID.randomUUID();
        }
        return new EavFieldSpec(identity, field.key(), field.label(), dataType, field.required(), field.constraints());
    }

    private static void rejectCommonFieldCollisions(List<TicketFieldSpec> fields) {
        for (TicketFieldSpec field : fields) {
            rejectCommonFieldKey(field.key());
        }
    }

    private static void rejectEavCommonFieldCollisions(List<EavFieldSpec> fields) {
        for (EavFieldSpec field : fields) {
            rejectCommonFieldKey(field.key());
        }
    }

    private static void rejectCommonFieldKey(String key) {
        if (COMMON_FIELD_KEYS.contains(key)) {
            throw new IllegalArgumentException("Custom field key collides with Ticket common field '" + key + "'");
        }
    }

    private static TicketTypeInfo toInfo(TicketType type) {
        return new TicketTypeInfo(
            type.id(),
            type.key(),
            type.name(),
            type.status(),
            type.createdAt(),
            type.updatedAt()
        );
    }

    private static TicketTemplateInfo toTemplateInfo(EavSchemaVersionInfo template) {
        return new TicketTemplateInfo(
            template.id(),
            template.version(),
            toTemplateStatus(template.status()),
            template.fields().stream().map(TicketTypeServiceImpl::toFieldInfo).toList(),
            template.publishedAt(),
            template.retiredAt()
        );
    }

    private static TicketFieldInfo toFieldInfo(EavFieldSpec field) {
        return new TicketFieldInfo(
            field.fieldId(),
            field.key(),
            field.label(),
            toFieldType(field.dataType()),
            field.required(),
            field.constraints()
        );
    }

    private static TicketFieldType toFieldType(EavDataType dataType) {
        return switch (dataType) {
            case STRING -> TicketFieldType.STRING;
            case NUMBER -> TicketFieldType.NUMBER;
            case BOOLEAN -> TicketFieldType.BOOLEAN;
            case DATE -> TicketFieldType.DATE;
            case DATETIME -> TicketFieldType.DATETIME;
            case UUID -> TicketFieldType.UUID;
            case ENUM -> TicketFieldType.ENUM;
        };
    }

    private static EavDataType toEavDataType(TicketFieldType dataType) {
        return switch (dataType) {
            case STRING -> EavDataType.STRING;
            case NUMBER -> EavDataType.NUMBER;
            case BOOLEAN -> EavDataType.BOOLEAN;
            case DATE -> EavDataType.DATE;
            case DATETIME -> EavDataType.DATETIME;
            case UUID -> EavDataType.UUID;
            case ENUM -> EavDataType.ENUM;
        };
    }

    private static TicketTemplateStatus toTemplateStatus(EavSchemaStatus status) {
        return switch (status) {
            case DRAFT -> TicketTemplateStatus.DRAFT;
            case PUBLISHED -> TicketTemplateStatus.PUBLISHED;
            case RETIRED -> TicketTemplateStatus.RETIRED;
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

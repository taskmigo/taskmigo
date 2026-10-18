package io.taskmigo.eav.value.application;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaService;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.application.EavTransactionRunner;
import io.taskmigo.eav.schema.domain.EavFieldDefinition;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.schema.domain.EavValue;
import io.taskmigo.eav.schema.domain.EavValueValidator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Applies schema validation and historical-version pinning for generic EAV instances.
public final class EavInstanceServiceImpl implements EavInstanceService {

    private final EavSchemaService schemas;
    private final EavInstanceRepository instances;
    private final EavValueValidator validator;
    private final EavTransactionRunner transactions;

    public EavInstanceServiceImpl(
        EavSchemaService schemas,
        EavInstanceRepository instances,
        EavValueValidator validator,
        EavTransactionRunner transactions
    ) {
        this.schemas = Objects.requireNonNull(schemas);
        this.instances = Objects.requireNonNull(instances);
        this.validator = Objects.requireNonNull(validator);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public EavInstanceView create(UUID definitionId, UUID versionId, Map<String, ? extends @Nullable Object> values) {
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(versionId);
        Map<String, @Nullable Object> supplied = new LinkedHashMap<>(Objects.requireNonNull(values));
        return this.transactions.write(() -> {
            EavSchemaVersionInfo version = this.schemas.version(definitionId, versionId);
            if (version.status() != EavSchemaStatus.PUBLISHED) {
                throw new IllegalStateException("New EAV instances require a published schema version");
            }
            Map<String, EavFieldSpec> fields = fieldsByKey(version);
            rejectUnknownKeys(supplied, fields);

            Map<UUID, EavValue> normalized = new LinkedHashMap<>();
            for (EavFieldSpec field : version.fields()) {
                this.validator
                    .validate(toDefinition(field), supplied.get(field.key()))
                    .ifPresent(value -> normalized.put(field.fieldId(), value));
            }

            EavInstanceState state = new EavInstanceState(UUID.randomUUID(), definitionId, versionId, normalized);
            this.instances.create(state);
            return toView(state, version);
        });
    }

    @Override
    public EavInstanceView get(UUID instanceId) {
        Objects.requireNonNull(instanceId);
        return this.transactions.read(() -> {
            EavInstanceState state = this.requireState(instanceId);
            EavSchemaVersionInfo version = this.schemas.version(state.schemaDefinitionId(), state.schemaVersionId());
            return toView(state, version);
        });
    }

    @Override
    public EavInstanceView patch(UUID instanceId, Map<String, ? extends @Nullable Object> changes) {
        Objects.requireNonNull(instanceId);
        Objects.requireNonNull(changes);
        return this.transactions.write(() -> {
            EavInstanceState current = this.requireState(instanceId);
            EavSchemaVersionInfo version = this.schemas.version(
                current.schemaDefinitionId(),
                current.schemaVersionId()
            );
            Map<String, EavFieldSpec> fields = fieldsByKey(version);
            rejectUnknownKeys(changes, fields);

            Map<UUID, EavValue> updated = new LinkedHashMap<>(current.values());
            for (Map.Entry<String, ? extends @Nullable Object> change : changes.entrySet()) {
                EavFieldSpec field = requireField(fields, change.getKey());
                this.validator
                    .validate(toDefinition(field), change.getValue())
                    .ifPresentOrElse(
                        value -> updated.put(field.fieldId(), value),
                        () -> updated.remove(field.fieldId())
                    );
            }

            EavInstanceState state = new EavInstanceState(
                current.id(),
                current.schemaDefinitionId(),
                current.schemaVersionId(),
                updated
            );
            this.instances.update(state);
            return toView(state, version);
        });
    }

    private EavInstanceState requireState(UUID instanceId) {
        return this.instances.find(instanceId).orElseThrow(() -> new IllegalArgumentException("Unknown EAV instance"));
    }

    private static Map<String, EavFieldSpec> fieldsByKey(EavSchemaVersionInfo version) {
        Map<String, EavFieldSpec> fields = new LinkedHashMap<>();
        for (EavFieldSpec field : version.fields()) {
            fields.put(field.key(), field);
        }
        return fields;
    }

    private static void rejectUnknownKeys(
        Map<String, ? extends @Nullable Object> values,
        Map<String, EavFieldSpec> fields
    ) {
        for (String key : values.keySet()) {
            requireField(fields, key);
        }
    }

    private static EavFieldSpec requireField(Map<String, EavFieldSpec> fields, String key) {
        EavFieldSpec field = fields.get(key);
        if (field == null) {
            throw new IllegalArgumentException("Unknown EAV field key '" + key + "'");
        }
        return field;
    }

    private static EavFieldDefinition toDefinition(EavFieldSpec field) {
        return new EavFieldDefinition(
            field.fieldId(),
            field.key(),
            field.label(),
            field.dataType(),
            field.required(),
            field.constraints()
        );
    }

    private static EavInstanceView toView(EavInstanceState state, EavSchemaVersionInfo version) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (EavFieldSpec field : version.fields()) {
            EavValue value = state.values().get(field.fieldId());
            if (value != null) {
                values.put(field.key(), rawValue(value));
            }
        }
        return new EavInstanceView(state.id(), state.schemaDefinitionId(), state.schemaVersionId(), values);
    }

    private static Object rawValue(EavValue value) {
        return switch (value) {
            case EavValue.StringValue string -> string.value();
            case EavValue.NumberValue number -> number.value();
            case EavValue.BooleanValue bool -> bool.value();
            case EavValue.DateValue date -> date.value();
            case EavValue.DateTimeValue dateTime -> dateTime.value();
            case EavValue.UuidValue uuid -> uuid.value();
            case EavValue.EnumValue option -> option.value();
        };
    }
}

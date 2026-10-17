package io.taskmigo.eav.schema.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Owns the lifecycle and immutable-publication boundary of one EAV schema revision.
public final class EavSchemaVersion {

    private final UUID id;
    private final UUID definitionId;
    private final long version;
    private List<EavFieldDefinition> fields;
    private EavSchemaStatus status;
    private @Nullable Instant publishedAt;
    private @Nullable Instant retiredAt;

    private EavSchemaVersion(
        UUID id,
        UUID definitionId,
        long version,
        List<EavFieldDefinition> fields,
        EavSchemaStatus status
    ) {
        this.id = Objects.requireNonNull(id);
        this.definitionId = Objects.requireNonNull(definitionId);
        if (version < 1) {
            throw new IllegalArgumentException("EAV schema version must be positive");
        }
        this.version = version;
        this.status = Objects.requireNonNull(status);
        this.fields = checkedFields(fields);
    }

    /// Creates an editable draft revision.
    public static EavSchemaVersion draft(UUID id, UUID definitionId, long version, List<EavFieldDefinition> fields) {
        return new EavSchemaVersion(id, definitionId, version, fields, EavSchemaStatus.DRAFT);
    }

    public UUID id() {
        return this.id;
    }

    public UUID definitionId() {
        return this.definitionId;
    }

    public long version() {
        return this.version;
    }

    public List<EavFieldDefinition> fields() {
        return this.fields;
    }

    public EavSchemaStatus status() {
        return this.status;
    }

    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(this.publishedAt);
    }

    public Optional<Instant> retiredAt() {
        return Optional.ofNullable(this.retiredAt);
    }

    /// Replaces the complete field set while this revision is still a draft.
    public void replaceFields(List<EavFieldDefinition> fields) {
        this.requireStatus(EavSchemaStatus.DRAFT, "Only a draft schema version can be edited");
        this.fields = checkedFields(fields);
    }

    /// Makes this revision immutable and eligible for selection as a creation schema.
    public void publish(Instant publishedAt) {
        this.requireStatus(EavSchemaStatus.DRAFT, "Only a draft schema version can be published");
        this.status = EavSchemaStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt);
    }

    /// Retires a previously published revision without deleting its historical definition.
    public void retire(Instant retiredAt) {
        this.requireStatus(EavSchemaStatus.PUBLISHED, "Only a published schema version can be retired");
        this.status = EavSchemaStatus.RETIRED;
        this.retiredAt = Objects.requireNonNull(retiredAt);
    }

    private void requireStatus(EavSchemaStatus expected, String message) {
        if (this.status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private static List<EavFieldDefinition> checkedFields(List<EavFieldDefinition> fields) {
        List<EavFieldDefinition> copy = List.copyOf(Objects.requireNonNull(fields));
        Set<UUID> ids = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (EavFieldDefinition field : copy) {
            if (!ids.add(field.fieldId())) {
                throw new IllegalArgumentException("Duplicate EAV logical field identity: " + field.fieldId());
            }
            if (!keys.add(field.key())) {
                throw new IllegalArgumentException("Duplicate EAV field key: " + field.key());
            }
        }
        return copy;
    }
}

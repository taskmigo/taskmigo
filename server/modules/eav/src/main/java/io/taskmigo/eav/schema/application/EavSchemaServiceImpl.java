package io.taskmigo.eav.schema.application;

import io.taskmigo.eav.schema.domain.EavFieldDefinition;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.eav.schema.domain.EavSchemaVersion;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Implements versioned EAV schema lifecycle rules without depending on persistence or Spring.
public final class EavSchemaServiceImpl implements EavSchemaService {

    private final EavSchemaRepository repository;
    private final EavTransactionRunner transactions;
    private final Clock clock;

    public EavSchemaServiceImpl(EavSchemaRepository repository, EavTransactionRunner transactions, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.transactions = Objects.requireNonNull(transactions);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public UUID createDefinition(String key, String name) {
        String normalizedKey = requireNonBlank(key, "EAV schema definition key");
        String normalizedName = requireNonBlank(name, "EAV schema definition name");
        return this.transactions.write(() -> {
            UUID id = UUID.randomUUID();
            this.repository.createDefinition(id, normalizedKey, normalizedName);
            return id;
        });
    }

    @Override
    public EavSchemaVersionInfo createDraft(UUID definitionId) {
        Objects.requireNonNull(definitionId);
        return this.transactions.write(() -> {
            this.repository.lockDefinition(definitionId);
            List<EavFieldSpec> fields = this.repository
                .current(definitionId)
                .map(EavSchemaVersionInfo::fields)
                .orElse(List.of());
            long version = this.repository.nextVersion(definitionId);
            EavSchemaVersionInfo draft = EavSchemaVersionInfo.draft(UUID.randomUUID(), definitionId, version, fields);
            validateFields(draft);
            this.repository.insertVersion(draft);
            return draft;
        });
    }

    @Override
    public EavSchemaVersionInfo replaceDraftFields(UUID definitionId, UUID versionId, List<EavFieldSpec> fields) {
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(versionId);
        List<EavFieldSpec> replacement = List.copyOf(Objects.requireNonNull(fields));
        return this.transactions.write(() -> {
            this.repository.lockDefinition(definitionId);
            EavSchemaVersionInfo existing = this.requireVersion(definitionId, versionId);
            requireStatus(existing, EavSchemaStatus.DRAFT, "Only a draft schema version can be edited");
            for (EavFieldSpec field : replacement) {
                this.repository.publishedField(definitionId, field.fieldId()).ifPresent(published -> {
                    if (!published.key().equals(field.key()) || published.dataType() != field.dataType()) {
                        throw new IllegalArgumentException(
                            "Published EAV field identity cannot change key or data type: " + field.fieldId()
                        );
                    }
                });
            }
            EavSchemaVersionInfo changed = new EavSchemaVersionInfo(
                existing.id(),
                existing.definitionId(),
                existing.version(),
                existing.status(),
                replacement,
                existing.publishedAt(),
                existing.retiredAt()
            );
            validateFields(changed);
            this.repository.updateVersion(changed);
            return changed;
        });
    }

    @Override
    public EavSchemaVersionInfo publish(UUID definitionId, UUID versionId) {
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(versionId);
        return this.transactions.write(() -> {
            this.repository.lockDefinition(definitionId);
            EavSchemaVersionInfo draft = this.requireVersion(definitionId, versionId);
            requireStatus(draft, EavSchemaStatus.DRAFT, "Only a draft schema version can be published");
            validateFields(draft);
            EavSchemaVersionInfo published = new EavSchemaVersionInfo(
                draft.id(),
                draft.definitionId(),
                draft.version(),
                EavSchemaStatus.PUBLISHED,
                draft.fields(),
                Optional.of(this.now()),
                Optional.empty()
            );
            this.repository.updateVersion(published);
            this.repository.setCurrentVersion(definitionId, published.id());
            return published;
        });
    }

    @Override
    public EavSchemaVersionInfo retire(UUID definitionId, UUID versionId) {
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(versionId);
        return this.transactions.write(() -> {
            this.repository.lockDefinition(definitionId);
            EavSchemaVersionInfo published = this.requireVersion(definitionId, versionId);
            requireStatus(published, EavSchemaStatus.PUBLISHED, "Only a published schema version can be retired");
            EavSchemaVersionInfo retired = new EavSchemaVersionInfo(
                published.id(),
                published.definitionId(),
                published.version(),
                EavSchemaStatus.RETIRED,
                published.fields(),
                published.publishedAt(),
                Optional.of(this.now())
            );
            this.repository.updateVersion(retired);
            if (
                this.repository
                    .current(definitionId)
                    .map(EavSchemaVersionInfo::id)
                    .filter(versionId::equals)
                    .isPresent()
            ) {
                this.repository.setCurrentVersion(definitionId, null);
            }
            return retired;
        });
    }

    @Override
    public Optional<EavSchemaVersionInfo> current(UUID definitionId) {
        Objects.requireNonNull(definitionId);
        return this.transactions.read(() -> this.repository.current(definitionId));
    }

    @Override
    public List<EavSchemaVersionInfo> listVersions(UUID definitionId) {
        Objects.requireNonNull(definitionId);
        return this.transactions.read(() -> this.repository.findVersions(definitionId));
    }

    @Override
    public EavSchemaVersionInfo version(UUID definitionId, UUID versionId) {
        Objects.requireNonNull(definitionId);
        Objects.requireNonNull(versionId);
        return this.transactions.read(() -> this.requireVersion(definitionId, versionId));
    }

    private EavSchemaVersionInfo requireVersion(UUID definitionId, UUID versionId) {
        return this.repository
            .findVersion(definitionId, versionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown EAV schema version"));
    }

    private Instant now() {
        return this.clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static void validateFields(EavSchemaVersionInfo version) {
        List<EavFieldDefinition> definitions = version.fields().stream().map(EavFieldSpec::toDefinition).toList();
        EavSchemaVersion.draft(version.id(), version.definitionId(), version.version(), definitions);
    }

    private static void requireStatus(EavSchemaVersionInfo version, EavSchemaStatus status, String message) {
        if (version.status() != status) {
            throw new IllegalStateException(message);
        }
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}

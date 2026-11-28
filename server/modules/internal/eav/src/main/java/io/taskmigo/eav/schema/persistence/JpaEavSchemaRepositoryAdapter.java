package io.taskmigo.eav.schema.persistence;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaRepository;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
class JpaEavSchemaRepositoryAdapter implements EavSchemaRepository {

    private final JpaEavSchemaDefinitionRepository definitions;
    private final JpaEavSchemaVersionRepository versions;
    private final JpaEavFieldDefinitionRepository fields;

    JpaEavSchemaRepositoryAdapter(
        JpaEavSchemaDefinitionRepository definitions,
        JpaEavSchemaVersionRepository versions,
        JpaEavFieldDefinitionRepository fields
    ) {
        this.definitions = definitions;
        this.versions = versions;
        this.fields = fields;
    }

    @Override
    public void createDefinition(UUID id, String key, String name) {
        this.definitions.save(new EavSchemaDefinitionEntity(id, key, name, Instant.now()));
    }

    @Override
    public boolean definitionExists(UUID definitionId) {
        return this.definitions.existsById(definitionId);
    }

    @Override
    public void lockDefinition(UUID definitionId) {
        this.definitions
            .findFirstById(definitionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown EAV schema definition"));
    }

    @Override
    public long nextVersion(UUID definitionId) {
        return this.versions
            .findTopBySchemaDefinitionIdOrderByVersionDesc(definitionId)
            .map(version -> version.version() + 1)
            .orElse(1L);
    }

    @Override
    public void insertVersion(EavSchemaVersionInfo version) {
        this.versions.save(toEntity(version));
        this.saveFields(version);
    }

    @Override
    public void updateVersion(EavSchemaVersionInfo version) {
        EavSchemaVersionEntity entity = this.versions
            .findByIdAndSchemaDefinitionId(version.id(), version.definitionId())
            .orElseThrow(() -> new IllegalArgumentException("Unknown EAV schema version"));
        boolean replaceFields = entity.status() == EavSchemaStatus.DRAFT && version.status() == EavSchemaStatus.DRAFT;
        entity.updateLifecycle(version.status(), version.publishedAt().orElse(null), version.retiredAt().orElse(null));
        this.versions.save(entity);
        if (replaceFields) {
            this.fields.deleteAllBySchemaVersionId(version.id());
            this.fields.flush();
            this.saveFields(version);
        }
    }

    @Override
    public Optional<EavSchemaVersionInfo> findVersion(UUID definitionId, UUID versionId) {
        return this.versions.findByIdAndSchemaDefinitionId(versionId, definitionId).map(this::toInfo);
    }

    @Override
    public List<EavSchemaVersionInfo> findVersions(UUID definitionId) {
        return this.versions
            .findAllBySchemaDefinitionIdOrderByVersionAsc(definitionId)
            .stream()
            .map(this::toInfo)
            .toList();
    }

    @Override
    public Optional<EavSchemaVersionInfo> current(UUID definitionId) {
        return this.definitions
            .findById(definitionId)
            .map(EavSchemaDefinitionEntity::currentVersionId)
            .flatMap(versionId -> this.findVersion(definitionId, versionId));
    }

    @Override
    public List<EavSchemaVersionInfo> currentVersions(Collection<UUID> definitionIds) {
        if (definitionIds.isEmpty()) {
            return List.of();
        }
        List<EavSchemaVersionEntity> current = this.versions.findAll((root, query, builder) -> {
            var selected = Objects.requireNonNull(query).subquery(UUID.class);
            var definition = selected.from(EavSchemaDefinitionEntity.class);
            selected.select(definition.get(EavSchemaDefinitionEntity_.id));
            selected.where(
                definition.get(EavSchemaDefinitionEntity_.id).in(definitionIds),
                builder.equal(
                    definition.get(EavSchemaDefinitionEntity_.currentVersionId),
                    root.get(EavSchemaVersionEntity_.id)
                ),
                builder.equal(
                    definition.get(EavSchemaDefinitionEntity_.id),
                    root.get(EavSchemaVersionEntity_.schemaDefinitionId)
                )
            );
            return builder.exists(selected);
        });
        if (current.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<EavFieldDefinitionEntity>> grouped = this.fields
            .findAllBySchemaVersionIdInOrderByKey(current.stream().map(EavSchemaVersionEntity::id).toList())
            .stream()
            .collect(Collectors.groupingBy(field -> field.schemaVersionId));
        return current
            .stream()
            .map(version -> toInfo(version, grouped.getOrDefault(version.id(), List.of())))
            .toList();
    }

    @Override
    public Optional<EavFieldSpec> publishedField(UUID definitionId, UUID fieldId) {
        List<UUID> versionIds = this.versions
            .findAllBySchemaDefinitionIdAndStatusNot(definitionId, EavSchemaStatus.DRAFT)
            .stream()
            .map(EavSchemaVersionEntity::id)
            .toList();
        if (versionIds.isEmpty()) {
            return Optional.empty();
        }
        return this.fields
            .findFirstBySchemaVersionIdInAndFieldId(versionIds, fieldId)
            .map(EavFieldDefinitionEntity::toSpec);
    }

    @Override
    public void setCurrentVersion(UUID definitionId, @Nullable UUID versionId) {
        EavSchemaDefinitionEntity definition = this.definitions
            .findById(definitionId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown EAV schema definition"));
        definition.selectCurrent(versionId, Instant.now());
        this.definitions.save(definition);
    }

    private EavSchemaVersionInfo toInfo(EavSchemaVersionEntity entity) {
        return toInfo(entity, this.fields.findAllBySchemaVersionIdOrderByKey(entity.id()));
    }

    private static EavSchemaVersionInfo toInfo(EavSchemaVersionEntity entity, List<EavFieldDefinitionEntity> fields) {
        return new EavSchemaVersionInfo(
            entity.id(),
            entity.schemaDefinitionId(),
            entity.version(),
            entity.status(),
            fields.stream().map(EavFieldDefinitionEntity::toSpec).toList(),
            Optional.ofNullable(entity.publishedAt()),
            Optional.ofNullable(entity.retiredAt())
        );
    }

    private void saveFields(EavSchemaVersionInfo version) {
        this.fields.saveAll(
            version
                .fields()
                .stream()
                .map(field -> new EavFieldDefinitionEntity(UUID.randomUUID(), version.id(), field))
                .toList()
        );
    }

    private static EavSchemaVersionEntity toEntity(EavSchemaVersionInfo version) {
        return new EavSchemaVersionEntity(
            version.id(),
            version.definitionId(),
            version.version(),
            version.status(),
            version.publishedAt().orElse(null),
            version.retiredAt().orElse(null)
        );
    }
}

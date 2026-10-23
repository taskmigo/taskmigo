package io.taskmigo.eav.value.persistence;

import io.taskmigo.eav.schema.domain.EavValue;
import io.taskmigo.eav.value.application.EavInstanceRepository;
import io.taskmigo.eav.value.application.EavInstanceState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaEavInstanceRepositoryAdapter implements EavInstanceRepository {

    private final JpaEavSchemaInstanceRepository instances;
    private final JpaEavFieldValueRepository values;

    JpaEavInstanceRepositoryAdapter(JpaEavSchemaInstanceRepository instances, JpaEavFieldValueRepository values) {
        this.instances = instances;
        this.values = values;
    }

    @Override
    public void create(EavInstanceState state) {
        if (this.instances.existsById(state.id())) {
            throw new IllegalArgumentException("EAV instance already exists");
        }
        this.instances.saveAndFlush(
            new EavSchemaInstanceEntity(state.id(), state.schemaDefinitionId(), state.schemaVersionId())
        );
        this.saveValues(state);
    }

    @Override
    public Optional<EavInstanceState> find(UUID instanceId) {
        return this.instances.findById(instanceId).map(this::toState);
    }

    @Override
    public void update(EavInstanceState state) {
        EavSchemaInstanceEntity existing = this.instances
            .findById(state.id())
            .orElseThrow(() -> new IllegalArgumentException("Unknown EAV instance"));
        if (
            !existing.schemaDefinitionId().equals(state.schemaDefinitionId()) ||
            !existing.schemaVersionId().equals(state.schemaVersionId())
        ) {
            throw new IllegalArgumentException("EAV instance schema identity is immutable");
        }
        this.values.deleteAllByIdSchemaInstanceId(state.id());
        this.values.flush();
        this.saveValues(state);
    }

    private EavInstanceState toState(EavSchemaInstanceEntity entity) {
        Map<UUID, EavValue> stored = new LinkedHashMap<>();
        for (EavFieldValueEntity value : this.values.findAllByIdSchemaInstanceId(entity.id())) {
            stored.put(value.fieldId(), value.toValue());
        }
        return new EavInstanceState(entity.id(), entity.schemaDefinitionId(), entity.schemaVersionId(), stored);
    }

    private void saveValues(EavInstanceState state) {
        this.values.saveAll(
            state
                .values()
                .entrySet()
                .stream()
                .map(entry ->
                    new EavFieldValueEntity(state.id(), state.schemaVersionId(), entry.getKey(), entry.getValue())
                )
                .toList()
        );
    }
}

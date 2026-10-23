package io.taskmigo.eav.query;

import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.query.QueryOperator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/// Resolves only query fields whose logical identity and type remain unambiguous across a requested schema scope.
public final class EavQueryFieldCatalog {

    public Map<String, EavQueryField> resolve(List<EavSchemaVersionInfo> versions) {
        List<EavSchemaVersionInfo> eligible = List.copyOf(Objects.requireNonNull(versions))
            .stream()
            .filter(version -> version.status() != EavSchemaStatus.DRAFT)
            .toList();
        Map<String, List<Occurrence>> occurrences = new LinkedHashMap<>();
        for (EavSchemaVersionInfo version : eligible) {
            for (EavFieldSpec field : version.fields()) {
                occurrences
                    .computeIfAbsent(field.key(), ignored -> new ArrayList<>())
                    .add(new Occurrence(version.definitionId(), version.id(), field));
            }
        }

        TreeMap<String, EavQueryField> result = new TreeMap<>();
        for (Map.Entry<String, List<Occurrence>> entry : occurrences.entrySet()) {
            List<Occurrence> values = entry.getValue();
            Set<UUID> definitions = values.stream().map(Occurrence::definitionId).collect(Collectors.toSet());
            Set<UUID> fieldIds = values
                .stream()
                .map(value -> value.field().fieldId())
                .collect(Collectors.toSet());
            Set<EavDataType> dataTypes = values
                .stream()
                .map(value -> value.field().dataType())
                .collect(Collectors.toSet());
            if (definitions.size() != 1 || fieldIds.size() != 1 || dataTypes.size() != 1) {
                continue;
            }

            EavFieldSpec representative = values.getFirst().field();
            boolean presentEverywhere = eligible.stream().allMatch(version ->
                version
                    .fields()
                    .stream()
                    .anyMatch(
                        field ->
                            field.key().equals(representative.key()) &&
                            field.fieldId().equals(representative.fieldId()) &&
                            field.dataType() == representative.dataType()
                    )
            );
            boolean nullable = !presentEverywhere || values.stream().anyMatch(value -> !value.field().required());
            Capabilities capabilities = capabilities(representative.dataType());
            result.put(
                representative.key(),
                new EavQueryField(
                    representative.fieldId(),
                    representative.key(),
                    representative.dataType(),
                    nullable,
                    capabilities.operators(),
                    capabilities.sortable()
                )
            );
        }
        return Map.copyOf(result);
    }

    private static Capabilities capabilities(EavDataType type) {
        return switch (type) {
            case STRING -> new Capabilities(
                EnumSet.of(QueryOperator.EQ, QueryOperator.NE, QueryOperator.CONTAINS),
                true
            );
            case NUMBER, DATE, DATETIME -> new Capabilities(
                EnumSet.of(
                    QueryOperator.EQ,
                    QueryOperator.NE,
                    QueryOperator.GT,
                    QueryOperator.GTE,
                    QueryOperator.LT,
                    QueryOperator.LTE
                ),
                true
            );
            case BOOLEAN, UUID, ENUM -> new Capabilities(EnumSet.of(QueryOperator.EQ, QueryOperator.NE), false);
        };
    }

    private record Occurrence(UUID definitionId, UUID versionId, EavFieldSpec field) {}

    private record Capabilities(Set<QueryOperator> operators, boolean sortable) {
        private Capabilities {
            operators = Set.copyOf(operators);
        }
    }
}

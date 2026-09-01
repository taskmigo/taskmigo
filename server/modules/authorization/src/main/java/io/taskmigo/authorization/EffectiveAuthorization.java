package io.taskmigo.authorization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Holds deduplicated executable Statements while retaining every assignment and inheritance provenance path.
public final class EffectiveAuthorization {

    private final Map<String, List<EffectiveStatement>> statementsByMethod;

    public EffectiveAuthorization(List<EffectiveStatement> statements) {
        Map<String, List<EffectiveStatement>> indexed = new LinkedHashMap<>();
        for (EffectiveStatement statement : statements) {
            indexed.computeIfAbsent(statement.statement().method(), ignored -> new ArrayList<>()).add(statement);
        }
        Map<String, List<EffectiveStatement>> immutable = new LinkedHashMap<>();
        indexed.forEach((method, values) -> immutable.put(method, List.copyOf(values)));
        this.statementsByMethod = Map.copyOf(immutable);
    }

    List<EffectiveStatement> forMethod(String method) {
        return this.statementsByMethod.getOrDefault(method, List.of());
    }

    public record EffectiveStatement(CompiledStatement statement, List<Provenance> provenance) {
        public EffectiveStatement {
            provenance = List.copyOf(provenance);
        }
    }

    public record Provenance(List<String> path) {
        public Provenance {
            path = List.copyOf(path);
        }
    }
}

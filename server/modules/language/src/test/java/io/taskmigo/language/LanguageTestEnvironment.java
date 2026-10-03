package io.taskmigo.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

final class LanguageTestEnvironment {

    private LanguageTestEnvironment() {}

    static CompilerEnvironment environment(Map<String, RootSpec> roots) {
        return CompilerEnvironment.of(
            roots
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build()))
        );
    }

    static RootSpec resource(String type, boolean symbolic, FieldSpec... fields) {
        return new RootSpec(type, null, false, symbolic, List.of(fields));
    }

    static RootSpec value(String type, LanguageType valueType, boolean symbolic) {
        return new RootSpec(type, valueType, false, symbolic, List.of());
    }

    static FieldSpec field(String path, LanguageType type, boolean nullable) {
        return new FieldSpec(path, type, nullable);
    }

    record RootSpec(
        String resourceType,
        @Nullable LanguageType valueType,
        boolean nullable,
        boolean symbolic,
        List<FieldSpec> fields
    ) {
        CompilerEnvironment.Root build() {
            ResourceType type = ResourceType.of(this.resourceType);
            List<Field> declarations = new ArrayList<>();
            for (FieldSpec field : this.fields) {
                declarations.add(
                    new Field(
                        FieldId.of(this.resourceType + ":" + field.path()),
                        FieldPath.parse(field.path()),
                        field.type(),
                        field.nullable()
                    )
                );
            }
            ResourceSchema schema = ResourceSchema.of(type, declarations);
            return this.valueType == null
                ? new CompilerEnvironment.Root(schema, this.symbolic)
                : new CompilerEnvironment.Root(schema, this.valueType, this.nullable, this.symbolic);
        }
    }

    record FieldSpec(String path, LanguageType type, boolean nullable) {}
}

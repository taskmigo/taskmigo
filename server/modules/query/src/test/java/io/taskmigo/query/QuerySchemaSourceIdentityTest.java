package io.taskmigo.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuerySchemaSourceIdentityTest {

    @Test
    @DisplayName("schema identity differs when equal public fields resolve to different logical sources")
    void shouldIncludeOpaqueSourceIdentity() {
        QueryFieldDescriptor first = new QueryFieldDescriptor(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            Set.of(QueryOperator.EQ),
            false,
            "field-a:STRING"
        );
        QueryFieldDescriptor second = new QueryFieldDescriptor(
            QueryPath.of("severity"),
            TypeDescriptor.of(String.class),
            true,
            Set.of(QueryOperator.EQ),
            false,
            "field-b:STRING"
        );

        assertThat(schema(first).identity()).isNotEqualTo(schema(second).identity());
    }

    private static QuerySchemaView schema(QueryFieldDescriptor field) {
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return "tickets.list";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of(field);
            }
        };
    }
}

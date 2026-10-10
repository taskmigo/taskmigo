package io.taskmigo.identity.group.adapter.out.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryPath;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupOperationQuerySchemaTest {

    @Mock private SingularAttribute<GroupEntity, UUID> id;
    @Mock private SingularAttribute<GroupEntity, String> code;
    @Mock private SingularAttribute<GroupEntity, String> displayName;
    @Mock private SingularAttribute<GroupEntity, String> description;

    @BeforeEach
    void configureMetamodel() {
        configure(this.id, "id", UUID.class, false);
        configure(this.code, "code", String.class, false);
        configure(this.displayName, "displayName", String.class, false);
        configure(this.description, "description", String.class, true);
        GroupEntity_.id = this.id;
        GroupEntity_.code = this.code;
        GroupEntity_.displayName = this.displayName;
        GroupEntity_.description = this.description;
    }

    @Test
    @DisplayName("exposes only approved Group fields")
    void shouldExposeOnlyApprovedGroupFields() {
        var schema = new ListGroupsQuerySchema();
        assertThat(schema.field(QueryPath.parse("code"))).get().satisfies(field ->
            assertThat(field.operators()).containsExactlyInAnyOrder(EQ, NE, CONTAINS)
        );
        assertThat(schema.field(QueryPath.parse("childGroups"))).isEmpty();
    }

    private static <V> void configure(
        SingularAttribute<GroupEntity, V> attribute,
        String name,
        Class<V> type,
        boolean optional
    ) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.getJavaType()).thenReturn(type);
        when(attribute.isOptional()).thenReturn(optional);
    }
}

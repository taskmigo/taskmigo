package io.taskmigo.authorization.role.adapter.out.persistence;

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
class RoleOperationQuerySchemaTest {

    @Mock private SingularAttribute<RoleEntity, UUID> id;
    @Mock private SingularAttribute<RoleEntity, String> code;
    @Mock private SingularAttribute<RoleEntity, String> displayName;
    @Mock private SingularAttribute<RoleEntity, String> description;

    @BeforeEach
    void configureMetamodel() {
        configure(this.id, "id", UUID.class, false);
        configure(this.code, "code", String.class, false);
        configure(this.displayName, "displayName", String.class, false);
        configure(this.description, "description", String.class, true);
        RoleEntity_.id = this.id;
        RoleEntity_.code = this.code;
        RoleEntity_.displayName = this.displayName;
        RoleEntity_.description = this.description;
    }

    @Test
    @DisplayName("exposes only approved Role fields")
    void shouldExposeOnlyApprovedRoleFields() {
        var schema = new ListRolesQuerySchema();
        assertThat(schema.field(QueryPath.parse("displayName"))).get().satisfies(field ->
            assertThat(field.operators()).containsExactlyInAnyOrder(EQ, NE, CONTAINS)
        );
        assertThat(schema.field(QueryPath.parse("statementIds"))).isEmpty();
    }

    private static <V> void configure(
        SingularAttribute<RoleEntity, V> attribute,
        String name,
        Class<V> type,
        boolean optional
    ) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.getJavaType()).thenReturn(type);
        when(attribute.isOptional()).thenReturn(optional);
    }
}

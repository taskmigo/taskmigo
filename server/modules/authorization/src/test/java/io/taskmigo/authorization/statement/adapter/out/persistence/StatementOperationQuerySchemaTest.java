package io.taskmigo.authorization.statement.adapter.out.persistence;

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
class StatementOperationQuerySchemaTest {

    @Mock private SingularAttribute<StatementEntity, UUID> id;
    @Mock private SingularAttribute<StatementEntity, String> code;
    @Mock private SingularAttribute<StatementEntity, String> description;
    @Mock private SingularAttribute<StatementEntity, String> method;
    @Mock private SingularAttribute<StatementEntity, String> path;

    @BeforeEach
    void configureMetamodel() {
        configure(this.id, "id", UUID.class, false);
        configure(this.code, "code", String.class, false);
        configure(this.description, "description", String.class, true);
        configure(this.method, "method", String.class, false);
        configure(this.path, "path", String.class, false);
        StatementEntity_.id = this.id;
        StatementEntity_.code = this.code;
        StatementEntity_.description = this.description;
        StatementEntity_.method = this.method;
        StatementEntity_.path = this.path;
    }

    @Test
    @DisplayName("uses JPA-backed Statement paths without DTO aliases")
    void shouldExposeJpaBackedStatementPathsWithoutDtoAliases() {
        var schema = new ListStatementsQuerySchema();
        assertThat(schema.field(QueryPath.parse("method"))).get().satisfies(field ->
            assertThat(field.operators()).containsExactlyInAnyOrder(EQ, NE, CONTAINS)
        );
        assertThat(schema.field(QueryPath.parse("target.api.method"))).isEmpty();
        assertThat(schema.field(QueryPath.parse("policy"))).isEmpty();
    }

    private static <V> void configure(
        SingularAttribute<StatementEntity, V> attribute,
        String name,
        Class<V> type,
        boolean optional
    ) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.getJavaType()).thenReturn(type);
        when(attribute.isOptional()).thenReturn(optional);
    }
}

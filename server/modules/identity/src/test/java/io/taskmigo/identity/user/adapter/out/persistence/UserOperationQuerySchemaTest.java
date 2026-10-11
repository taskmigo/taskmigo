package io.taskmigo.identity.user.adapter.out.persistence;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.query.QueryPath;
import jakarta.persistence.metamodel.SingularAttribute;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserOperationQuerySchemaTest {

    @Mock
    private SingularAttribute<UserEntity, UUID> id;

    @Mock
    private SingularAttribute<UserEntity, String> username;

    @Mock
    private SingularAttribute<UserEntity, String> firstName;

    @Mock
    private SingularAttribute<UserEntity, String> lastName;

    @Mock
    private SingularAttribute<UserEntity, UserStatus> status;

    @Mock
    private SingularAttribute<UserEntity, Instant> retainedAt;

    @BeforeEach
    void configureGeneratedMetamodel() {
        configure(this.id, "id", UUID.class, false);
        configure(this.username, "username", String.class, true);
        configure(this.firstName, "firstName", String.class, true);
        configure(this.lastName, "lastName", String.class, true);
        configure(this.status, "status", UserStatus.class, false);
        configure(this.retainedAt, "retainedAt", Instant.class, true);
        UserEntity_.id = this.id;
        UserEntity_.username = this.username;
        UserEntity_.firstName = this.firstName;
        UserEntity_.lastName = this.lastName;
        UserEntity_.status = this.status;
        UserEntity_.retainedAt = this.retainedAt;
    }

    @Test
    @DisplayName("keeps User query schemas scoped to their concrete operations")
    void shouldKeepUserSchemasDistinctWhenEntityRootIsShared() {
        var list = new ListUsersQuerySchema();
        var delete = new DeleteUserQuerySchema();

        assertThat(list.rootType()).isEqualTo(UserEntity.class);
        assertThat(delete.rootType()).isEqualTo(UserEntity.class);
        assertThat(list.identity()).isNotEqualTo(delete.identity());
        assertThat(list.field(QueryPath.parse("firstName"))).isPresent();
        assertThat(delete.field(QueryPath.parse("firstName"))).isEmpty();
    }

    @Test
    @DisplayName("derives List Users fields and capabilities from the generated JPA metamodel")
    void shouldExposeListUserFieldsFromGeneratedJpaMetamodel() {
        var schema = new ListUsersQuerySchema();

        assertThat(UserEntity_.class.getName()).endsWith("UserEntity_");
        assertThat(schema.field(QueryPath.parse("username")))
            .get()
            .satisfies(field -> {
                assertThat(field.type().rawType()).isEqualTo(String.class);
                assertThat(field.nullable()).isTrue();
                assertThat(field.operators()).containsExactlyInAnyOrder(EQ, NE, CONTAINS);
            });
        assertThat(schema.binding(QueryPath.parse("username")))
            .get()
            .satisfies(field -> assertThat(field.jpaPath().javaType()).isEqualTo(String.class));
    }

    @Test
    @DisplayName("keeps undeclared User persistence attributes outside the operation schema")
    void shouldDenyUndeclaredUserPersistenceField() {
        var schema = new ListUsersQuerySchema();

        assertThat(schema.field(QueryPath.parse("passwordHash"))).isEmpty();
        assertThat(schema.binding(QueryPath.parse("passwordHash"))).isEmpty();
    }

    private static <V> void configure(
        SingularAttribute<UserEntity, V> attribute,
        String name,
        Class<V> javaType,
        boolean optional
    ) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.getJavaType()).thenReturn(javaType);
        when(attribute.isOptional()).thenReturn(optional);
    }
}

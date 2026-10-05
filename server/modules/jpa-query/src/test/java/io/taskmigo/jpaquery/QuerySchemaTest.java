package io.taskmigo.jpaquery;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.IN;
import static io.taskmigo.query.QueryOperator.NE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryPath;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuerySchemaTest {

    @Mock
    private SingularAttribute<TestEntity, UUID> id;

    @Mock
    private SingularAttribute<TestEntity, String> username;

    @Mock
    private SingularAttribute<TestEntity, Organization> organization;

    @Mock
    private SingularAttribute<Organization, String> organizationName;

    @Mock
    private CollectionAttribute<TestEntity, Organization> organizations;

    /**
     * Verifies that static JPA metadata is the source of an operation schema's path, Java type, nullability, and
     * expression capabilities.
     *
     * Given: a list-users-like schema that declares id, username, and organization.name from SingularAttribute
     * metadata.
     * Expect: the effective schema exposes those paths with inferred metadata and preserves the explicitly declared
     * operator allowlist.
     */
    @Test
    @DisplayName("derives query field metadata from typed JPA attributes")
    void shouldDeriveQueryFieldMetadataWhenStaticJpaAttributesAreDeclared() {
        // Arrange
        configureTestSchemaMetamodel();
        QuerySchema<TestEntity> schema = new TestSchema(
            this.id,
            this.username,
            this.organization,
            this.organizationName
        );

        // Act
        var fields = schema.fields(QueryFieldContext.empty());

        // Assert
        assertThat(schema.rootType()).isEqualTo(TestEntity.class);
        assertThat(fields)
            .extracting(field -> field.path().text())
            .containsExactlyInAnyOrder("id", "username", "organization.name");
        assertThat(schema.field(QueryPath.parse("username"), QueryFieldContext.empty()))
            .get()
            .satisfies(field -> {
                assertThat(field.type().rawType()).isEqualTo(String.class);
                assertThat(field.nullable()).isTrue();
                assertThat(field.operators()).containsExactlyInAnyOrder(EQ, NE, IN, CONTAINS);
            });
    }

    /**
     * Verifies that a JPA attribute which exists on the entity is still unavailable unless the operation explicitly
     * declares it.
     *
     * Given: an operation schema that does not declare an internalNote attribute.
     * Expect: resolving internalNote returns no field, preserving default-deny behavior.
     */
    @Test
    @DisplayName("keeps undeclared entity attributes outside the query surface")
    void shouldReturnNoFieldWhenEntityAttributeIsUndeclared() {
        // Arrange
        configureTestSchemaMetamodel();
        QuerySchema<TestEntity> schema = new TestSchema(
            this.id,
            this.username,
            this.organization,
            this.organizationName
        );

        // Act
        var result = schema.field(QueryPath.parse("internalNote"), QueryFieldContext.empty());

        // Assert
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that two static declarations cannot expose the same effective path.
     *
     * Given: a schema whose static field source declares username twice.
     * Expect: effective schema resolution fails before any query compilation can use ambiguous metadata.
     */
    @Test
    @DisplayName("rejects duplicate static query paths")
    void shouldRejectDuplicatePathWhenStaticFieldsCollide() {
        // Arrange
        configureLeaf(this.username, "username", String.class, true);
        QuerySchema<TestEntity> schema = new DuplicateStaticSchema(this.username);

        // Act + Assert
        assertThatThrownBy(() -> schema.fields(QueryFieldContext.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("username");
    }

    /**
     * Verifies that runtime fields use the same no-shadowing rule as static fields.
     *
     * Given: a schema whose runtime field source declares id while the static field source already declares id.
     * Expect: effective schema resolution rejects the collision rather than overriding either declaration.
     */
    @Test
    @DisplayName("rejects collisions between static and runtime query paths")
    void shouldRejectDuplicatePathWhenRuntimeFieldShadowsStaticField() {
        // Arrange
        configureLeaf(this.id, "id", UUID.class, false);
        QuerySchema<TestEntity> schema = new RuntimeCollisionSchema(this.id);

        // Act + Assert
        assertThatThrownBy(() -> schema.fields(QueryFieldContext.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("id");
    }

    /**
     * Verifies that nested singular attributes are represented as one type-safe JPA path.
     *
     * Given: a path from TestEntity.organization to Organization.name.
     * Expect: the exposed logical path is organization.name and its leaf Java type is String.
     */
    @Test
    @DisplayName("supports typed nested singular JPA paths")
    void shouldExposeNestedPathWhenEverySegmentIsSingular() {
        // Arrange
        configureSegment(this.organization, "organization", false);
        configureLeaf(this.organizationName, "name", String.class, false);
        JpaPath<TestEntity, String> path = JpaPath.of(this.organization).then(this.organizationName);

        // Act
        QueryField<TestEntity, String> field = QueryField.of(path).operators(EQ, CONTAINS);

        // Assert
        assertThat(field.descriptor().path().text()).isEqualTo("organization.name");
        assertThat(field.descriptor().type().rawType()).isEqualTo(String.class);
    }

    /**
     * Verifies that collection-valued relationship traversal is rejected in this enhancement.
     *
     * Given: a CollectionAttribute representing a one-to-many or many-to-many association.
     * Expect: creating a query path from that attribute fails immediately.
     */
    @Test
    @DisplayName("rejects collection-valued JPA paths")
    void shouldRejectJpaPathWhenAttributeIsCollectionValued() {
        // Arrange
        when(this.organizations.getName()).thenReturn("organizations");
        CollectionAttribute<TestEntity, Organization> collection = this.organizations;

        // Act + Assert
        assertThatThrownBy(() -> JpaPath.of(collection))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("collection-valued");
    }

    private void configureTestSchemaMetamodel() {
        configureLeaf(this.id, "id", UUID.class, false);
        configureLeaf(this.username, "username", String.class, true);
        configureSegment(this.organization, "organization", false);
        configureLeaf(this.organizationName, "name", String.class, false);
    }

    private static <O, V> void configureLeaf(
        SingularAttribute<O, V> attribute,
        String name,
        Class<V> javaType,
        boolean optional
    ) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.getJavaType()).thenReturn(javaType);
        when(attribute.isOptional()).thenReturn(optional);
    }

    private static <O, V> void configureSegment(SingularAttribute<O, V> attribute, String name, boolean optional) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.isOptional()).thenReturn(optional);
    }

    private static final class TestSchema extends QuerySchema<TestEntity> {

        private final SingularAttribute<TestEntity, UUID> id;
        private final SingularAttribute<TestEntity, String> username;
        private final SingularAttribute<TestEntity, Organization> organization;
        private final SingularAttribute<Organization, String> organizationName;

        private TestSchema(
            SingularAttribute<TestEntity, UUID> id,
            SingularAttribute<TestEntity, String> username,
            SingularAttribute<TestEntity, Organization> organization,
            SingularAttribute<Organization, String> organizationName
        ) {
            this.id = id;
            this.username = username;
            this.organization = organization;
            this.organizationName = organizationName;
        }

        @Override
        protected String operationId() {
            return "test.list";
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> staticFields() {
            return List.of(
                field(this.id).operators(EQ, NE, IN),
                field(this.username).operators(EQ, NE, IN, CONTAINS),
                field(JpaPath.of(this.organization).then(this.organizationName)).operators(EQ, CONTAINS)
            );
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
            return List.of();
        }
    }

    private static final class DuplicateStaticSchema extends QuerySchema<TestEntity> {

        private final SingularAttribute<TestEntity, String> username;

        private DuplicateStaticSchema(SingularAttribute<TestEntity, String> username) {
            this.username = username;
        }

        @Override
        protected String operationId() {
            return "test.duplicate-static";
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> staticFields() {
            return List.of(field(this.username).operators(EQ), field(this.username).operators(NE));
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
            return List.of();
        }
    }

    private static final class RuntimeCollisionSchema extends QuerySchema<TestEntity> {

        private final SingularAttribute<TestEntity, UUID> id;

        private RuntimeCollisionSchema(SingularAttribute<TestEntity, UUID> id) {
            this.id = id;
        }

        @Override
        protected String operationId() {
            return "test.runtime-collision";
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> staticFields() {
            return List.of(field(this.id).operators(EQ));
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
            return List.of(field(this.id).operators(NE));
        }
    }

    private static final class TestEntity {}

    private static final class Organization {}
}

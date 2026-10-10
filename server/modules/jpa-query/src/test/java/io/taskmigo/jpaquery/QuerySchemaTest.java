package io.taskmigo.jpaquery;

import static io.taskmigo.query.QueryOperator.CONTAINS;
import static io.taskmigo.query.QueryOperator.EQ;
import static io.taskmigo.query.QueryOperator.NE;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.BASIC;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.EMBEDDED;
import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.CollectionAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QuerySchemaTest {

    private static final Set<String> APPROVED_FIELD_OPERATORS = Set.of(
        "EQ",
        "NE",
        "GT",
        "GTE",
        "LT",
        "LTE",
        "CONTAINS"
    );

    @Mock
    private SingularAttribute<TestEntity, UUID> id;

    @Mock
    private SingularAttribute<TestEntity, String> username;

    @Mock
    private SingularAttribute<TestEntity, Organization> organization;

    @Mock
    private SingularAttribute<Organization, String> organizationName;

    @Mock
    private SingularAttribute<TestEntity, Profile> profile;

    @Mock
    private SingularAttribute<Profile, String> profileLabel;

    @Mock
    private SingularAttribute<String, String> invalidUsernameChild;

    @Mock
    private CollectionAttribute<TestEntity, Organization> organizations;

    @Mock
    private CollectionAttribute<Organization, String> organizationTags;

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
                assertThat(field.operators()).containsExactlyInAnyOrder(EQ, NE, CONTAINS);
            });
    }

    /**
     * Verifies that JPA query fields can expose only the comparison capabilities approved by Enhancement #237.
     *
     * Given: all QueryOperator values currently available to the repository.
     * Expect: GTE/LTE exist and a JPA query field accepts only EQ, NE, GT, GTE, LT, LTE, and CONTAINS.
     */
    @Test
    @DisplayName("limits JPA query fields to approved comparison capabilities")
    void shouldRejectQueryFieldOperatorWhenCapabilityIsNotApproved() {
        // Arrange
        configureLeaf(this.username, "username", String.class, true);
        QueryField<TestEntity, String> field = QueryField.of(JpaPath.of(this.username));
        Set<String> availableOperators = Arrays.stream(QueryOperator.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

        // Act + Assert
        assertThat(availableOperators).containsAll(APPROVED_FIELD_OPERATORS);
        for (QueryOperator operator : QueryOperator.values()) {
            if (APPROVED_FIELD_OPERATORS.contains(operator.name())) {
                assertThat(field.operators(operator).descriptor().operators()).containsExactly(operator);
            } else {
                assertThatThrownBy(() -> field.operators(operator))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(operator.name());
            }
        }
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
     * Verifies that runtime fields cannot collide with each other either.
     *
     * Given: a schema whose runtime source returns the same path twice.
     * Expect: effective schema resolution rejects the duplicate path.
     */
    @Test
    @DisplayName("rejects duplicate runtime query paths")
    void shouldRejectDuplicatePathWhenRuntimeFieldsCollide() {
        // Arrange
        configureLeaf(this.username, "username", String.class, true);
        QuerySchema<TestEntity> schema = new DuplicateRuntimeSchema(this.username);

        // Act + Assert
        assertThatThrownBy(() -> schema.fields(QueryFieldContext.empty()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("username");
    }

    /**
     * Verifies that nested singular attributes are represented as one type-safe JPA path.
     *
     * Given: a path from TestEntity.organization to Organization.name.
     * Expect: the exposed logical path is organization.name and its leaf Java type is String.
     */
    @Test
    @DisplayName("supports typed nested to-one JPA paths")
    void shouldExposeNestedPathWhenIntermediateSegmentIsToOne() {
        // Arrange
        configureSegment(this.organization, "organization", false, MANY_TO_ONE);
        configureLeaf(this.organizationName, "name", String.class, false);
        JpaPath<TestEntity, String> path = JpaPath.of(this.organization).then(this.organizationName);

        // Act
        QueryField<TestEntity, String> field = QueryField.of(path).operators(EQ, CONTAINS);

        // Assert
        assertThat(field.descriptor().path().text()).isEqualTo("organization.name");
        assertThat(field.descriptor().type().rawType()).isEqualTo(String.class);
    }

    /**
     * Verifies that embedded attributes are valid intermediate path segments.
     *
     * Given: a path from TestEntity.profile to embedded Profile.label.
     * Expect: the typed path is accepted and exposes profile.label.
     */
    @Test
    @DisplayName("supports typed nested embedded JPA paths")
    void shouldExposeNestedPathWhenIntermediateSegmentIsEmbedded() {
        // Arrange
        configureSegment(this.profile, "profile", false, EMBEDDED);
        configureLeaf(this.profileLabel, "label", String.class, false);
        JpaPath<TestEntity, String> path = JpaPath.of(this.profile).then(this.profileLabel);

        // Act
        QueryField<TestEntity, String> field = QueryField.of(path).operators(EQ, CONTAINS);

        // Assert
        assertThat(field.descriptor().path().text()).isEqualTo("profile.label");
        assertThat(field.descriptor().type().rawType()).isEqualTo(String.class);
    }

    /**
     * Verifies that a basic scalar cannot be traversed merely because both segments are SingularAttribute values.
     *
     * Given: username is a BASIC String attribute and a synthetic child attribute is supplied after it.
     * Expect: nested path construction rejects the non-embedded/non-to-one intermediate segment.
     */
    @Test
    @DisplayName("rejects traversal through basic singular attributes")
    void shouldRejectNestedPathWhenIntermediateSegmentIsBasic() {
        // Arrange
        when(this.username.getName()).thenReturn("username");
        when(this.username.getPersistentAttributeType()).thenReturn(BASIC);

        // Act + Assert
        assertThatThrownBy(() -> JpaPath.of(this.username).then(this.invalidUsernameChild))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("username");
    }

    /**
     * Verifies that collection-valued relationship traversal is rejected in this enhancement.
     *
     * Given: a CollectionAttribute representing a one-to-many or many-to-many association.
     * Expect: creating a query path from that attribute fails immediately.
     */
    @Test
    @DisplayName("rejects root collection-valued JPA paths")
    void shouldRejectJpaPathWhenRootAttributeIsCollectionValued() {
        // Arrange
        when(this.organizations.getName()).thenReturn("organizations");
        CollectionAttribute<TestEntity, Organization> collection = this.organizations;

        // Act + Assert
        assertThatThrownBy(() -> JpaPath.of(collection))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("collection-valued");
    }

    /**
     * Verifies that collection-valued traversal is rejected after a singular segment as well.
     *
     * Given: TestEntity.organization is singular and Organization.tags is collection-valued.
     * Expect: appending the collection segment fails immediately.
     */
    @Test
    @DisplayName("rejects nested collection-valued JPA paths")
    void shouldRejectJpaPathWhenNestedAttributeIsCollectionValued() {
        // Arrange
        when(this.organizationTags.getName()).thenReturn("tags");

        // Act + Assert
        assertThatThrownBy(() -> JpaPath.of(this.organization).then(this.organizationTags))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("collection-valued");
    }

    /**
     * Verifies that schema identity is scoped to the concrete query operation even when entity roots are shared.
     *
     * Given: two schemas backed by TestEntity with different operation identifiers.
     * Expect: they have distinct identities and therefore cannot collapse into an entity-global schema.
     */
    @Test
    @DisplayName("keeps schemas distinct per query operation")
    void shouldKeepSchemaIdentityDistinctWhenEntityRootIsShared() {
        // Arrange
        configureLeaf(this.id, "id", UUID.class, false);
        QuerySchema<TestEntity> list = new OperationSchema("test.list", this.id);
        QuerySchema<TestEntity> resolve = new OperationSchema("test.resolve", this.id);

        // Act + Assert
        assertThat(list.rootType()).isEqualTo(resolve.rootType()).isEqualTo(TestEntity.class);
        assertThat(list.identity()).isNotEqualTo(resolve.identity());
    }

    private void configureTestSchemaMetamodel() {
        configureLeaf(this.id, "id", UUID.class, false);
        configureLeaf(this.username, "username", String.class, true);
        configureSegment(this.organization, "organization", false, MANY_TO_ONE);
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

    private static <O, V> void configureSegment(
        SingularAttribute<O, V> attribute,
        String name,
        boolean optional,
        PersistentAttributeType attributeType
    ) {
        when(attribute.getName()).thenReturn(name);
        when(attribute.isOptional()).thenReturn(optional);
        when(attribute.getPersistentAttributeType()).thenReturn(attributeType);
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
                field(this.id).operators(EQ, NE),
                field(this.username).operators(EQ, NE, CONTAINS),
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

    private static final class DuplicateRuntimeSchema extends QuerySchema<TestEntity> {

        private final SingularAttribute<TestEntity, String> username;

        private DuplicateRuntimeSchema(SingularAttribute<TestEntity, String> username) {
            this.username = username;
        }

        @Override
        protected String operationId() {
            return "test.duplicate-runtime";
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> staticFields() {
            return List.of();
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
            return List.of(field(this.username).operators(EQ), field(this.username).operators(NE));
        }
    }

    private static final class OperationSchema extends QuerySchema<TestEntity> {

        private final String operation;
        private final SingularAttribute<TestEntity, UUID> id;

        private OperationSchema(String operation, SingularAttribute<TestEntity, UUID> id) {
            this.operation = operation;
            this.id = id;
        }

        @Override
        protected String operationId() {
            return this.operation;
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> staticFields() {
            return List.of(field(this.id).operators(EQ));
        }

        @Override
        protected Collection<QueryField<TestEntity, ?>> runtimeFields(QueryFieldContext context) {
            return List.of();
        }
    }

    private static final class TestEntity {}

    private static final class Organization {}

    private static final class Profile {}
}

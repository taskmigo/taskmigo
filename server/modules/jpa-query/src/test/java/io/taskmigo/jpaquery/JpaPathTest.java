package io.taskmigo.jpaquery;

import static jakarta.persistence.metamodel.Attribute.PersistentAttributeType.MANY_TO_ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaPathTest {

    @Mock
    private Path<TestEntity> root;

    @Mock
    private Path<Organization> organizationPath;

    @Mock
    private Path<String> organizationNamePath;

    @Mock
    private SingularAttribute<TestEntity, Organization> organization;

    @Mock
    private SingularAttribute<Organization, String> organizationName;

    /// Verifies that Criteria resolution keeps generated metamodel attributes typed through every singular segment.
    @Test
    @DisplayName("resolves nested Criteria paths through typed metamodel attributes")
    void shouldResolveNestedCriteriaPathThroughTypedMetamodelAttributes() {
        // Arrange
        when(this.organization.getPersistentAttributeType()).thenReturn(MANY_TO_ONE);
        when(this.root.get(this.organization)).thenReturn(this.organizationPath);
        when(this.organizationPath.get(this.organizationName)).thenReturn(this.organizationNamePath);
        JpaPath<TestEntity, String> path = JpaPath.of(this.organization).then(this.organizationName);

        // Act
        Path<String> resolved = path.resolve(this.root);

        // Assert
        assertThat(resolved).isSameAs(this.organizationNamePath);
        verify(this.root).get(this.organization);
        verify(this.organizationPath).get(this.organizationName);
    }

    private static final class TestEntity {}

    private static final class Organization {}
}

package io.taskmigo.auth.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.AuthorizationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationResourceRegistryTest {

    private final JavaScriptPolicyEvaluator evaluator = new JavaScriptPolicyEvaluator();

    /**
     * Verifies that duplicate resource identities are resolved once and remain addressable by multiple names.
     *
     * Given: two user descriptors with different policy names but the same request-derived key.
     * Expect: the adapter receives one batch containing one key and both names receive the same safe value.
     */
    @Test
    @DisplayName("deduplicates resource lookups and batches by type")
    void shouldBatchResourceResolutionWhenDescriptorsShareIdentity() {
        // Arrange
        AtomicInteger calls = new AtomicInteger();
        AuthorizationResourceAdapter adapter = new AuthorizationResourceAdapter() {
            @Override
            public String type() {
                return "user";
            }

            @Override
            public Map<String, Map<String, ?>> resolve(Collection<String> keys) {
                calls.incrementAndGet();
                assertThat(keys).containsExactly("user-1");
                return Map.of("user-1", Map.of("username", "alice"));
            }
        };
        AuthorizationResourceRegistry registry = new AuthorizationResourceRegistry(this.evaluator, List.of(adapter));
        PolicyIr.Expression key = new PolicyIr.Reference("request", List.of("path", "userId"));
        ResourceDescriptor first = new ResourceDescriptor("user", "user", key);
        ResourceDescriptor second = new ResourceDescriptor("owner", "user", key);

        // Act
        ResolvedResources resolved = registry.resolve(
            List.of(first, second),
            Map.of("request", Map.of("path", Map.of("userId", "user-1")))
        );

        // Assert
        assertThat(calls).hasValue(1);
        assertThat(resolved.objectValues(List.of(first, second)))
            .containsOnlyKeys("user", "owner")
            .containsEntry("user", Map.of("username", "alice"))
            .containsEntry("owner", Map.of("username", "alice"));
    }

    /**
     * Verifies that multiple named resources of multiple types use one bounded batch per resource type.
     *
     * Given: two user resources and one project resource selected by one authorization operation.
     * Expect: each adapter is called once with all of its keys and every policy name receives its resolved value.
     */
    @Test
    @DisplayName("batches multiple named resources by type")
    void shouldBatchMultipleNamedResourcesWhenSeveralTypesAreSelected() {
        // Arrange
        AtomicInteger userCalls = new AtomicInteger();
        AtomicInteger projectCalls = new AtomicInteger();
        AuthorizationResourceAdapter users = adapter(
            "user",
            userCalls,
            Map.of("user-1", Map.of("username", "alice"), "user-2", Map.of("username", "bob")),
            Set.of("user-1", "user-2")
        );
        AuthorizationResourceAdapter projects = adapter(
            "project",
            projectCalls,
            Map.of("project-1", Map.of("ownerId", "user-1")),
            Set.of("project-1")
        );
        AuthorizationResourceRegistry registry = new AuthorizationResourceRegistry(
            this.evaluator,
            List.of(users, projects)
        );
        ResourceDescriptor firstUser = new ResourceDescriptor("requester", "user", new PolicyIr.Literal("user-1"));
        ResourceDescriptor secondUser = new ResourceDescriptor("owner", "user", new PolicyIr.Literal("user-2"));
        ResourceDescriptor project = new ResourceDescriptor("project", "project", new PolicyIr.Literal("project-1"));

        // Act
        ResolvedResources resolved = registry.resolve(List.of(firstUser, secondUser, project), Map.of());

        // Assert
        assertThat(userCalls).hasValue(1);
        assertThat(projectCalls).hasValue(1);
        assertThat(resolved.objectValues(List.of(firstUser, secondUser, project)))
            .containsEntry("requester", Map.of("username", "alice"))
            .containsEntry("owner", Map.of("username", "bob"))
            .containsEntry("project", Map.of("ownerId", "user-1"));
    }

    /**
     * Verifies that resource resolution fails closed when a selected resource type has no adapter.
     *
     * Given: a descriptor for an unregistered resource type.
     * Expect: resolution raises an authorization exception before any policy can be granted.
     */
    @Test
    @DisplayName("rejects resource types without adapters")
    void shouldRejectResourceResolutionWhenAdapterIsMissing() {
        // Arrange
        AuthorizationResourceRegistry registry = new AuthorizationResourceRegistry(this.evaluator, List.of());
        ResourceDescriptor descriptor = new ResourceDescriptor("project", "project", new PolicyIr.Literal("project-1"));

        // Act + Assert
        assertThatThrownBy(() -> registry.resolve(List.of(descriptor), Map.of()))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("no authorization resource adapter");
    }

    private static AuthorizationResourceAdapter adapter(
        String type,
        AtomicInteger calls,
        Map<String, Map<String, ?>> values,
        Set<String> expectedKeys
    ) {
        return new AuthorizationResourceAdapter() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public Map<String, Map<String, ?>> resolve(Collection<String> keys) {
                calls.incrementAndGet();
                assertThat(keys).containsExactlyInAnyOrderElementsOf(expectedKeys);
                return values;
            }
        };
    }
}

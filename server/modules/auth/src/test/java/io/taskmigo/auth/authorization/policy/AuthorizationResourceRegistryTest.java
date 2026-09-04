package io.taskmigo.auth.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.authorization.condition.AuthorizationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        assertThat(resolved.objectValues(List.of(first, second))).containsOnlyKeys("user", "owner")
            .containsEntry("user", Map.of("username", "alice"))
            .containsEntry("owner", Map.of("username", "alice"));
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
        ResourceDescriptor descriptor = new ResourceDescriptor(
            "project",
            "project",
            new PolicyIr.Literal("project-1")
        );

        // Act + Assert
        assertThatThrownBy(() -> registry.resolve(List.of(descriptor), Map.of()))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("no authorization resource adapter");
    }
}

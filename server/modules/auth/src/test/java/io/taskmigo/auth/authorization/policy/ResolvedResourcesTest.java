package io.taskmigo.auth.authorization.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResolvedResourcesTest {

    /**
     * Verifies that nested resource values are copied into an immutable policy boundary.
     *
     * Given: a resolved resource containing a mutable nested map and list.
     * Expect: later source mutations cannot change the value visible to policy evaluation.
     */
    @Test
    @DisplayName("deeply freezes resolved resource values")
    void shouldFreezeNestedValuesWhenResolutionResultIsCreated() {
        // Arrange
        Map<String, Object> nested = new HashMap<>();
        nested.put("name", "before");
        Map<String, Object> source = new HashMap<>();
        source.put("metadata", nested);
        source.put("labels", List.of("one"));
        ResourceKey key = new ResourceKey("project", "project-1");

        // Act
        ResolvedResources resolved = new ResolvedResources(Map.of(key, source), Map.of());
        nested.put("name", "after");

        // Assert
        Map<String, ?> value = Objects.requireNonNull(resolved.values().get(key));
        assertThat(value.get("metadata")).isEqualTo(Map.of("name", "before"));
        assertThat(value.get("labels")).isEqualTo(List.of("one"));
    }

    /**
     * Verifies that persistence objects cannot cross the resource adapter boundary.
     *
     * Given: a resource value containing an arbitrary host object.
     * Expect: resolution result construction rejects the value before policy evaluation.
     */
    @Test
    @DisplayName("rejects non-policy values in resolved resources")
    void shouldRejectUnsupportedValuesWhenResolutionResultIsCreated() {
        // Arrange
        ResourceKey key = new ResourceKey("project", "project-1");

        // Act + Assert
        assertThatThrownBy(() ->
            new ResolvedResources(Map.of(key, Map.of("entity", UUID.randomUUID().toString().getBytes())), Map.of())
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("immutable approved values");
    }
}

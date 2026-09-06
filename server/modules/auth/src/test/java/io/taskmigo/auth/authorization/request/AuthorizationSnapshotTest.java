package io.taskmigo.auth.authorization.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorizationSnapshotTest {

    /**
     * Verifies that a snapshot copies nested authorization input values at creation time.
     *
     * Given: mutable request and principal maps that are changed after snapshot creation.
     * Expect: the snapshot retains the original values and cannot be affected by later input mutations.
     */
    @Test
    @DisplayName("freezes authorization roots when creating a snapshot")
    void shouldFreezeAuthorizationRootsWhenSnapshotIsCreated() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        List<String> methods = new ArrayList<>(List.of("GET"));
        request.put("method", methods);
        Map<String, Object> roots = new HashMap<>();
        roots.put("request", request);
        List<StatementInfo> statements = List.of();
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(
            UUID.randomUUID(),
            statements,
            new StatementArtifactFactory(new JavaScriptPolicyCompiler()).build(statements),
            roots
        );

        // Act
        methods.add("POST");
        request.put("changed", true);
        roots.put("principal", Map.of("id", "changed"));

        // Assert
        assertThat(snapshot.roots()).containsOnlyKeys("request");
        assertThat(snapshot.roots().get("request")).isEqualTo(Map.of("method", List.of("GET")));
    }
}

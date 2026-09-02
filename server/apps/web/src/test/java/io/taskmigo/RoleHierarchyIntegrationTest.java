package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.role.RoleService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-id=integration-client",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-secret=integration-secret",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.cli.registration.authorization-grant-types=client_credentials",
        "spring.security.oauth2.authorizationserver.client.cli.registration.scopes=taskmigo.api",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RoleHierarchyIntegrationTest {

    private final RoleService access;
    private final JdbcTemplate jdbc;

    RoleHierarchyIntegrationTest(RoleService access, JdbcTemplate jdbc) {
        this.access = access;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("persists unique role edges and resolves transitive descendants")
    void shouldResolveTransitiveDescendantsWhenUniqueRoleEdgesArePersisted() {
        UUID root = role("RootRole");
        UUID left = role("LeftRole");
        UUID right = role("RightRole");
        UUID leaf = role("LeafRole");

        this.access.setChildRoles(root, List.of(right, left, left));
        this.access.setChildRoles(left, Set.of(leaf));
        this.access.setChildRoles(right, Set.of(leaf));

        assertThat(this.access.descendantRoles(root))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(left, right, leaf).stream().sorted().toList());
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_hierarchy where parent_role_id = ? and child_role_id = ?",
                Integer.class,
                root,
                left
            )
        ).isEqualTo(1);
    }

    @Test
    @DisplayName("rejects role cycles without changing existing edges")
    void shouldPreserveExistingEdgesWhenRoleCycleIsRejected() {
        UUID root = role("RootRole");
        UUID child = role("ChildRole");
        UUID leaf = role("LeafRole");
        this.access.setChildRoles(root, Set.of(child));
        this.access.setChildRoles(child, Set.of(leaf));

        assertThatThrownBy(() -> this.access.setChildRoles(root, Set.of(root)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Role hierarchy must be acyclic");
        assertThatThrownBy(() -> this.access.setChildRoles(leaf, Set.of(root)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Role hierarchy must be acyclic");

        assertThat(this.access.descendantRoles(root))
            .extracting(RoleInfo::id)
            .containsExactlyElementsOf(List.of(child, leaf).stream().sorted().toList());
        assertThat(this.access.descendantRoles(leaf)).isEmpty();
    }

    private UUID role(String name) {
        return this.access.createRole(name + UUID.randomUUID().toString().replace("-", ""), null, Set.of());
    }
}

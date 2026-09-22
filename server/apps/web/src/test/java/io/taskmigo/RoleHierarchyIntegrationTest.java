package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
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
        "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.oauth.signing-key-auto-create=true",
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
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_hierarchy_closure where ancestor_role_id = ? and descendant_role_id = ?",
                Integer.class,
                root,
                root
            )
        ).isEqualTo(1);
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_hierarchy_closure where ancestor_role_id = ? and descendant_role_id = ?",
                Integer.class,
                root,
                leaf
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
        assertThat(
            this.jdbc.queryForObject(
                "select count(*) from role_hierarchy_closure where ancestor_role_id = ? and descendant_role_id = ?",
                Integer.class,
                root,
                leaf
            )
        ).isEqualTo(1);
    }

    private UUID role(String name) {
        String code = name + UUID.randomUUID().toString().replace("-", "");
        return this.access.createRole(code, code, null, Set.of());
    }
}

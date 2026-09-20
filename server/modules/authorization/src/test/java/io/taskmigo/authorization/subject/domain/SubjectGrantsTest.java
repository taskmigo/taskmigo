package io.taskmigo.authorization.subject.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubjectGrantsTest {

    /**
     * Verifies that direct Role replacement is set-like and does not alter direct Statements.
     *
     * Given: one subject with an existing Statement and a requested Role collection containing duplicates.
     * Expect: the replacement contains one Role and preserves the existing Statement exactly.
     */
    @Test
    @DisplayName("replaces direct roles without changing statements")
    void shouldReplaceRolesWhenRequestedRoleIdsContainDuplicates() {
        // Arrange
        SubjectRef subject = new SubjectRef("identity:user", UUID.randomUUID());
        UUID roleId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        SubjectGrants grants = new SubjectGrants(subject, Set.of(), Set.of(statementId));

        // Act
        SubjectGrants replaced = grants.replacingRoles(List.of(roleId, roleId));

        // Assert
        assertThat(replaced.roleIds()).containsExactly(roleId);
        assertThat(replaced.statementIds()).containsExactly(statementId);
    }

    /**
     * Verifies that direct Statement replacement is set-like and does not alter direct Roles.
     *
     * Given: one subject with an existing Role and a requested Statement collection containing duplicates.
     * Expect: the replacement contains one Statement and preserves the existing Role exactly.
     */
    @Test
    @DisplayName("replaces direct statements without changing roles")
    void shouldReplaceStatementsWhenRequestedStatementIdsContainDuplicates() {
        // Arrange
        SubjectRef subject = new SubjectRef("identity:group", UUID.randomUUID());
        UUID roleId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        SubjectGrants grants = new SubjectGrants(subject, Set.of(roleId), Set.of());

        // Act
        SubjectGrants replaced = grants.replacingStatements(List.of(statementId, statementId));

        // Assert
        assertThat(replaced.roleIds()).containsExactly(roleId);
        assertThat(replaced.statementIds()).containsExactly(statementId);
    }
}

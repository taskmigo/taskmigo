package io.taskmigo.authorization.subject.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultSubjectGrantAssignmentServiceTest {

    /**
     * Verifies that Role references are validated before canonical direct-grant state is persisted.
     *
     * Given: an existing subject with one Statement and duplicate requested Role ids.
     * Expect: the Role service validates the deduplicated ids and the repository saves a set-like Role replacement.
     */
    @Test
    @DisplayName("validates role references before replacing direct role grants")
    void shouldValidateRolesWhenDirectRoleGrantsAreReplaced() {
        // Arrange
        SubjectRef subject = new SubjectRef("identity:user", UUID.randomUUID());
        UUID roleId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        RoleService roles = mock(RoleService.class);
        StatementService statements = mock(StatementService.class);
        SubjectGrantRepository grants = mock(SubjectGrantRepository.class);
        when(grants.load(subject)).thenReturn(new SubjectGrants(subject, Set.of(), Set.of(statementId)));
        var service = new DefaultSubjectGrantAssignmentService(roles, statements, grants);

        // Act
        service.setRoles(subject, List.of(roleId, roleId));

        // Assert
        verify(roles).requireRoles(Set.of(roleId));
        verify(grants).saveRoles(new SubjectGrants(subject, Set.of(roleId), Set.of(statementId)));
    }

    /**
     * Verifies that Statement references are validated before canonical direct-grant state is persisted.
     *
     * Given: an existing subject with one Role and duplicate requested Statement ids.
     * Expect: the Statement service validates deduplicated ids and the repository saves a set-like Statement replacement.
     */
    @Test
    @DisplayName("validates statement references before replacing direct statement grants")
    void shouldValidateStatementsWhenDirectStatementGrantsAreReplaced() {
        // Arrange
        SubjectRef subject = new SubjectRef("identity:user", UUID.randomUUID());
        UUID roleId = UUID.randomUUID();
        UUID statementId = UUID.randomUUID();
        RoleService roles = mock(RoleService.class);
        StatementService statements = mock(StatementService.class);
        SubjectGrantRepository grants = mock(SubjectGrantRepository.class);
        when(grants.load(subject)).thenReturn(new SubjectGrants(subject, Set.of(roleId), Set.of()));
        var service = new DefaultSubjectGrantAssignmentService(roles, statements, grants);

        // Act
        service.setStatements(subject, List.of(statementId, statementId));

        // Assert
        verify(statements).requireStatements(Set.of(statementId));
        verify(grants).saveStatements(new SubjectGrants(subject, Set.of(roleId), Set.of(statementId)));
    }
}

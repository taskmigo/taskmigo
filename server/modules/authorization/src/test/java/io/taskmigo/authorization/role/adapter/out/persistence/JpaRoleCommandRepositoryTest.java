package io.taskmigo.authorization.role.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.foundation.DomainFailureType;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JpaRoleCommandRepositoryTest {

    @Mock
    private RoleRepository roles;

    @InjectMocks
    private JpaRoleCommandRepository repository;

    /**
     * Verifies that a database integrity failure during Role insertion becomes a conflict while retaining its cause.
     *
     * Given: a new Role whose insert is rejected by the persistence repository with a data-integrity violation.
     * Expect: save throws a Role conflict whose direct cause is the original persistence exception.
     */
    @Test
    @DisplayName("translates role insert integrity violations to conflicts")
    void shouldThrowConflictWhenRoleInsertViolatesIntegrity() {
        // Arrange
        Role role = Role.create(UUID.randomUUID(), "duplicate_role", "Duplicate Role", null, Set.of());
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate role code");
        when(this.roles.findDistinctById(role.id())).thenReturn(Optional.empty());
        when(this.roles.saveAndFlush(any(RoleEntity.class))).thenThrow(cause);

        // Act + Assert
        assertThatThrownBy(() -> this.repository.save(role))
            .isInstanceOfSatisfying(RoleException.class, exception -> {
                assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT);
                assertThat(exception).hasMessage("Role code already exists");
                assertThat(exception.getCause()).isSameAs(cause);
            });
    }
}

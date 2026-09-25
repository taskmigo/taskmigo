package io.taskmigo.identity.group.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.domain.Group;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JpaGroupCommandRepositoryTest {

    @Mock
    private JpaGroupRepository groups;

    @InjectMocks
    private JpaGroupCommandRepository repository;

    /**
     * Verifies that a database integrity failure during Group insertion becomes a conflict while retaining its cause.
     *
     * Given: a new Group whose insert is rejected by the persistence repository with a data-integrity violation.
     * Expect: save throws a Group conflict whose direct cause is the original persistence exception.
     */
    @Test
    @DisplayName("translates group insert integrity violations to conflicts")
    void shouldThrowConflictWhenGroupInsertViolatesIntegrity() {
        // Arrange
        Group group = Group.create(UUID.randomUUID(), "duplicate_group", "Duplicate Group", null);
        DataIntegrityViolationException cause = new DataIntegrityViolationException("duplicate group code");
        when(this.groups.findById(group.id())).thenReturn(Optional.empty());
        when(this.groups.saveAndFlush(any(GroupEntity.class))).thenThrow(cause);

        // Act + Assert
        assertThatThrownBy(() -> this.repository.save(group))
            .isInstanceOfSatisfying(GroupException.class, exception -> {
                assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT);
                assertThat(exception).hasMessage("Group code already exists");
                assertThat(exception.getCause()).isSameAs(cause);
            });
    }
}

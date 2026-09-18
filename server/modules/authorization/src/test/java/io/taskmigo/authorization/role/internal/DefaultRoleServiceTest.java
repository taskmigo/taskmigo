package io.taskmigo.authorization.role.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultRoleServiceTest {

    @Mock
    private RoleStore roles;

    @InjectMocks
    private DefaultRoleService service;

    /**
     * Verifies that the application service delegates persistence through a framework-neutral RoleStore.
     *
     * Given: a Role creation request with no child roles and an empty persisted graph.
     * Expect: RoleStore receives one RoleState with the normalized name and no JPA type is exposed.
     */
    @Test
    @DisplayName("creates a role through the application-owned store")
    void shouldCreateRoleThroughStoreWhenRequestHasNoChildren() {
        // Arrange
        Mockito.when(roles.loadAllForUpdate()).thenReturn(List.of());
        ArgumentCaptor<RoleStore.RoleState> state = ArgumentCaptor.forClass(RoleStore.RoleState.class);

        // Act
        UUID id = service.createRole("  administrator  ", null, Set.of());

        // Assert
        Mockito.verify(roles).create(state.capture());
        assertThat(id).isEqualTo(state.getValue().id());
        assertThat(state.getValue().name()).isEqualTo("administrator");
        assertThat(state.getValue().childIds()).isEmpty();
    }
}


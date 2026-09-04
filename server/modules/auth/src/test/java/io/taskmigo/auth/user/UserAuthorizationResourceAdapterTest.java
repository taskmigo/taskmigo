package io.taskmigo.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserAuthorizationResourceAdapterTest {

    private final UserRepository users = mock(UserRepository.class);
    private final UserAuthorizationResourceAdapter adapter = new UserAuthorizationResourceAdapter(this.users);

    /**
     * Verifies that multiple user resource keys are loaded through one repository batch and exposed as safe values.
     *
     * Given: two persisted users selected by two request resources.
     * Expect: the repository is called once with both keys and the result contains no UserEntity values.
     */
    @Test
    @DisplayName("batches user resource resolution into one repository lookup")
    void shouldBatchUserResolutionWhenMultipleKeysAreRequested() {
        // Arrange
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        UserEntity first = new UserEntity(firstId, "alice", Set.of("alice@example.com"), Set.of(), "Alice", "A");
        UserEntity second = new UserEntity(secondId, "bob", Set.of("bob@example.com"), Set.of(), "Bob", "B");
        when(this.users.findAllById(any())).thenReturn(List.of(first, second));

        // Act
        Map<String, Map<String, ?>> result = this.adapter.resolve(List.of(firstId.toString(), secondId.toString()));

        // Assert
        verify(this.users, times(1)).findAllById(any());
        assertThat(result).containsOnlyKeys(firstId.toString(), secondId.toString());
        assertThat(Objects.requireNonNull(result.get(firstId.toString())).get("username")).isEqualTo("alice");
        assertThat(Objects.requireNonNull(result.get(secondId.toString())).get("username")).isEqualTo("bob");
        assertThat(result.values()).noneMatch(value -> value instanceof UserEntity);
    }
}

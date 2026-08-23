package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InternalClientDefinitionValidatorTest {

    @Test
    void rejectsMissingSecret() {
        var definition = new InternalClientDefinition("client", null, true, 1, Set.of("taskmigo.api"), Set.of());

        assertThatThrownBy(() -> InternalClientDefinitionValidator.validate("client", definition)).hasMessageContaining(
            "client-secret is required"
        );
    }

    @Test
    void rejectsUnknownServicePermission() {
        var definition = new InternalClientDefinition(
            "client",
            "secret",
            true,
            1,
            Set.of("taskmigo.api"),
            Set.of("unknown")
        );

        assertThatThrownBy(() -> InternalClientDefinitionValidator.validate("client", definition)).hasMessageContaining(
            "Unknown service permissions"
        );
    }
}

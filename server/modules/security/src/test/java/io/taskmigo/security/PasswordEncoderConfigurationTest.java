package io.taskmigo.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigurationTest {

    ///
    /// Verifies that the shared security configuration exposes exactly one Spring password encoder.
    ///
    /// Given: the shared password-encoder configuration loaded into an application context.
    /// Expect: one delegating encoder bean is available and produces a Spring-formatted encoded value.
    ///
    @Test
    @DisplayName("exposes one delegating password encoder")
    void shouldExposeSinglePasswordEncoderWhenConfigurationIsLoaded() {
        // Arrange
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(PasswordEncoderConfiguration.class);

            // Act
            context.refresh();

            // Assert
            assertThat(context.getBeansOfType(PasswordEncoder.class)).hasSize(1);
            assertThat(context.getBean(PasswordEncoder.class).encode("secret")).startsWith("{bcrypt}");
        }
    }
}

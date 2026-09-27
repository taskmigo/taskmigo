package io.taskmigo.foundation.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

class FoundationAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner().withConfiguration(
        AutoConfigurations.of(FoundationAutoConfiguration.class)
    );

    @Test
    @DisplayName("provides a password encoder when the consumer does not define one")
    void shouldProvidePasswordEncoderWhenConsumerDoesNotDefineOne() {
        this.context.run(application -> assertThat(application).hasSingleBean(PasswordEncoder.class));
    }
}

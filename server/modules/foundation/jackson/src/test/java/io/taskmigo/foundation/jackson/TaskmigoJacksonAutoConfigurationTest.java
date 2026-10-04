package io.taskmigo.foundation.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

class TaskmigoJacksonAutoConfigurationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(JacksonAutoConfiguration.class, TaskmigoJacksonAutoConfiguration.class)
        )
        .withPropertyValues("spring.jackson.time-zone=Asia/Ho_Chi_Minh");

    /**
     * Verifies that externally supplied Spring Jackson timezone configuration cannot change Taskmigo's UTC contract.
     *
     * Given: Spring is configured to use Asia/Ho_Chi_Minh for Jackson.
     * Expect: the auto-configured JsonMapper still uses UTC for serialization and deserialization.
     */
    @Test
    @DisplayName("keeps UTC when Spring Jackson timezone is configured to non-UTC")
    void shouldKeepUtcWhenSpringJacksonTimezoneIsConfiguredToNonUtc() {
        this.context.run(application -> {
            // Arrange
            assertThat(application.getBeansOfType(JsonMapper.class)).hasSize(1);

            // Act
            JsonMapper mapper = application.getBean(JsonMapper.class);

            // Assert
            assertThat(mapper.serializationConfig().getTimeZone().toZoneId().normalized()).isEqualTo(ZoneOffset.UTC);
            assertThat(mapper.deserializationConfig().getTimeZone().toZoneId().normalized()).isEqualTo(ZoneOffset.UTC);
        });
    }
}

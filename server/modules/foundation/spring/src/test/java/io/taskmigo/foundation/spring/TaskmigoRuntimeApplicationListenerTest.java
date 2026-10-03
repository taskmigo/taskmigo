package io.taskmigo.foundation.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class TaskmigoRuntimeApplicationListenerTest {

    /**
     * Verifies that the Spring Boot bootstrap path enforces Taskmigo's UTC runtime invariant without relying on an
     * executable application's main method.
     *
     * Given: both the user.timezone property and effective JVM default are deliberately set to Asia/Ho_Chi_Minh.
     * Expect: starting a minimal SpringApplication resets both values to UTC before the application context starts.
     */
    @Test
    @DisplayName("enforces UTC from the Spring Boot starting lifecycle")
    void shouldEnforceUtcWhenSpringApplicationStarts() {
        // Arrange
        String originalProperty = System.getProperty("user.timezone");
        TimeZone originalTimeZone = TimeZone.getDefault();
        System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        SpringApplication application = new SpringApplication(EmptyConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setLogStartupInfo(false);

        try {
            // Act
            try (ConfigurableApplicationContext ignored = application.run("--spring.main.banner-mode=off")) {
                // Assert
                assertThat(System.getProperty("user.timezone")).isEqualTo("UTC");
                assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
            }
        } finally {
            if (originalProperty == null) {
                System.clearProperty("user.timezone");
            } else {
                System.setProperty("user.timezone", originalProperty);
            }
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EmptyConfiguration {}
}

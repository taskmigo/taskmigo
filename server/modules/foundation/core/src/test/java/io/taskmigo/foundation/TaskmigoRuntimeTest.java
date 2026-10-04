package io.taskmigo.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TaskmigoRuntimeTest {

    /**
     * Verifies that Taskmigo overrides an operator-provided non-UTC JVM timezone before application bootstrap.
     *
     * Given: both the user.timezone property and effective JVM default are deliberately set to Asia/Ho_Chi_Minh.
     * Expect: runtime initialization changes both values to UTC.
     */
    @Test
    @DisplayName("enforces UTC when the JVM timezone was changed")
    void shouldEnforceUtcWhenJvmTimezoneWasChanged() {
        // Arrange
        String originalProperty = System.getProperty("user.timezone");
        TimeZone originalTimeZone = TimeZone.getDefault();
        System.setProperty("user.timezone", "Asia/Ho_Chi_Minh");
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        try {
            // Act
            TaskmigoRuntime.initialize();

            // Assert
            assertThat(System.getProperty("user.timezone")).isEqualTo("UTC");
            assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
        } finally {
            if (originalProperty == null) {
                System.clearProperty("user.timezone");
            } else {
                System.setProperty("user.timezone", originalProperty);
            }
            TimeZone.setDefault(originalTimeZone);
        }
    }
}

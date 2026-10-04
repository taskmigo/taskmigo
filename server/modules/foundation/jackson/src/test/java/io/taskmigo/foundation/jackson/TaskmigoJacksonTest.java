package io.taskmigo.foundation.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TaskmigoJacksonTest {

    /**
     * Verifies that Taskmigo's mapper policy is independent of the JVM default timezone.
     *
     * Given: the JVM default timezone has been deliberately changed to Asia/Ho_Chi_Minh.
     * Expect: a mapper built through the shared Taskmigo policy still uses UTC.
     */
    @Test
    @DisplayName("configures UTC when the JVM default timezone is non-UTC")
    void shouldConfigureUtcWhenJvmDefaultTimezoneIsNonUtc() {
        // Arrange
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        try {
            // Act
            JsonMapper mapper = TaskmigoJackson.configure(JsonMapper.builder()).build();

            // Assert
            assertThat(mapper.serializationConfig().getTimeZone().toZoneId().normalized()).isEqualTo(ZoneOffset.UTC);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}

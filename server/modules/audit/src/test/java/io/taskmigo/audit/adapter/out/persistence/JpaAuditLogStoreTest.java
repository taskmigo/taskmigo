package io.taskmigo.audit.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JpaAuditLogStoreTest {

    /**
     * Verifies that Audit persistence does not rely on Jackson's library defaults for Taskmigo-wide settings.
     *
     * Given: the dedicated Audit mapper is constructed by the persistence adapter.
     * Expect: its construction path explicitly depends on the shared Taskmigo Jackson policy.
     */
    @Test
    @DisplayName("applies the shared Taskmigo Jackson policy to Audit persistence")
    void shouldApplySharedTaskmigoJacksonPolicyWhenAuditMapperIsConstructed() {
        // Arrange
        var store = new ClassFileImporter().importClasses(JpaAuditLogStore.class).get(JpaAuditLogStore.class);

        // Act
        var dependencies = store
            .getDirectDependenciesFromSelf()
            .stream()
            .map(dependency -> dependency.getTargetClass().getName())
            .toList();

        // Assert
        assertThat(dependencies).contains("io.taskmigo.foundation.jackson.TaskmigoJackson");
    }


    /**
     * Given: historical User audit data containing actor username and raw profile values.
     * Expect: privacy scrubbing keeps event identity/history while removing User PII values.
     */
    @Test
    @DisplayName("scrubs historical user PII without deleting audit history")
    void shouldScrubHistoricalUserPii() {
        // Arrange
        UUID userId = UUID.randomUUID();
        AuditLogEntity log = new AuditLogEntity(
            UUID.randomUUID(),
            "user",
            userId,
            userId,
            "alice",
            Instant.parse("2026-10-01T00:00:00Z"),
            """
            [
              {"field":"firstName","before":"Alice","after":"Alicia","sensitive":false},
              {"field":"emails","before":["alice@example.com"],"after":[],"sensitive":false},
              {"field":"status","before":"ACTIVE","after":"RETAINED","sensitive":false}
            ]
            """
        );
        JpaAuditLogRepository repository = mock(JpaAuditLogRepository.class);
        when(repository.findAllByActorId(userId)).thenReturn(List.of(log));
        when(repository.findAllByEntityTypeAndEntityId("user", userId)).thenReturn(List.of(log));
        JpaAuditLogStore store = new JpaAuditLogStore(repository);

        // Act
        store.scrubUser(userId);

        // Assert
        assertThat(log.actorUsername).isEqualTo("Unknown user");
        assertThat(log.changesJson)
            .doesNotContain("Alice", "Alicia", "alice@example.com")
            .contains("\"field\":\"firstName\"", "\"field\":\"emails\"", "\"sensitive\":true")
            .contains("\"field\":\"status\"", "\"before\":\"ACTIVE\"", "\"after\":\"RETAINED\"");
    }
}

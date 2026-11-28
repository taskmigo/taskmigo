package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.DomainFailureType;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketPatch;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.field.application.api.TicketFieldException;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTemplateStatus;
import io.taskmigo.ticket.type.application.api.TicketTypeException;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(classes = TicketConcurrencyHistoryIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class TicketConcurrencyHistoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private static final ObjectAuthorizationPredicate<TicketInfo> ALLOW_ALL = new ObjectAuthorizationPredicate<>() {
        @Override
        public boolean isAlwaysTrue() {
            return true;
        }

        @Override
        public boolean isAlwaysFalse() {
            return false;
        }
    };

    private final TicketTypeService ticketTypes;
    private final TicketFieldService fieldService;
    private final TicketService tickets;
    private final JdbcTemplate jdbc;
    private final EntityManagerFactory entityManagerFactory;

    TicketConcurrencyHistoryIntegrationTest(
        TicketTypeService ticketTypes,
        TicketFieldService fieldService,
        TicketService tickets,
        JdbcTemplate jdbc,
        EntityManagerFactory entityManagerFactory
    ) {
        this.ticketTypes = ticketTypes;
        this.fieldService = fieldService;
        this.tickets = tickets;
        this.jdbc = jdbc;
        this.entityManagerFactory = entityManagerFactory;
    }

    @Test
    @DisplayName("stale ticket creation cannot silently switch to a newly activated template")
    void shouldRejectStaleTicketCreation() {
        UUID actor = insertUser("publish-create-race");
        TicketTypeInfo type = this.ticketTypes.create(
            "publish-create-race",
            "Publish create race",
            List.of(field("legacy", "Legacy", TicketFieldType.STRING, true))
        );
        UUID first = type.currentTemplate().id();
        UUID ticketId = this.tickets.create(type.id(), "Before", "OPEN", Map.of("legacy", "v1"), actor, first);
        TicketTemplateInfo next = this.ticketTypes.createTemplate(
            type.id(),
            first,
            List.of(field("next", "Next", TicketFieldType.STRING, true))
        );

        assertThatThrownBy(() ->
            this.tickets.create(type.id(), "Stale", "OPEN", Map.of("legacy", "v1"), actor, first)
        ).isInstanceOfSatisfying(TicketTypeException.class, exception ->
            assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT)
        );
        assertThat(this.tickets.read(ticketId, ALLOW_ALL, QueryFieldContext.empty()).templateVersionId()).isEqualTo(
            first
        );
        assertThat(this.ticketTypes.get(type.id()).currentTemplate().id()).isEqualTo(next.id());
    }

    @Test
    @DisplayName("concurrent replacement using the same base template allows exactly one activation")
    void shouldSerializeConcurrentActivation() throws Exception {
        TicketTypeInfo type = this.ticketTypes.create("double-publish", "Double publish", List.of());
        UUID previous = type.currentTemplate().id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<TicketTemplateInfo> first = executor.submit(() ->
                activateWhenReleased(type.id(), previous, ready, go)
            );
            Future<TicketTemplateInfo> second = executor.submit(() ->
                activateWhenReleased(type.id(), previous, ready, go)
            );
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                go.countDown();
            }
            int success = 0;
            int conflicts = 0;
            for (Future<TicketTemplateInfo> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    success++;
                } catch (ExecutionException exception) {
                    if (
                        exception.getCause() instanceof TicketTypeException failure &&
                        failure.type() == DomainFailureType.CONFLICT
                    ) {
                        conflicts++;
                    } else {
                        throw exception;
                    }
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
        } finally {
            go.countDown();
        }
        assertThat(this.ticketTypes.listTemplates(type.id()))
            .extracting(TicketTemplateInfo::status)
            .containsExactly(TicketTemplateStatus.SUPERSEDED, TicketTemplateStatus.ACTIVE);
    }

    @Test
    @DisplayName("historical values survive label rename field removal type replacement and retirement")
    void shouldPreserveHistoricalCorpusAcrossSchemaEvolution() {
        UUID actor = insertUser("historical-corpus");
        UUID severityV1Id = this.fieldService.create("severity", "Severity", TicketFieldType.STRING).id();
        UUID noteId = this.fieldService.create("note", "Note", TicketFieldType.STRING).id();
        TicketTypeInfo type = this.ticketTypes.create(
            "historical-corpus",
            "Historical corpus",
            List.of(
                new TicketFieldSpec(severityV1Id, null, true, Map.of()),
                new TicketFieldSpec(noteId, null, false, Map.of())
            )
        );
        UUID typeId = type.id();
        TicketTemplateInfo v1 = type.currentTemplate();
        UUID historicalId = this.tickets.create(
            typeId,
            "Historical",
            "OPEN",
            Map.of("severity", "legacy", "note", "keep me"),
            actor,
            v1.id()
        );

        TicketTemplateInfo v2 = this.ticketTypes.createTemplate(
            typeId,
            v1.id(),
            List.of(new TicketFieldSpec(severityV1Id, "Impact level", true, Map.of()))
        );
        UUID severityEnumId = this.fieldService.create("severityLevel", "Impact level", TicketFieldType.ENUM).id();
        TicketTemplateInfo v3 = this.ticketTypes.createTemplate(
            typeId,
            v2.id(),
            List.of(new TicketFieldSpec(severityEnumId, null, true, Map.of("options", List.of("HIGH", "LOW"))))
        );
        UUID currentId = this.tickets.create(
            typeId,
            "Current",
            "OPEN",
            Map.of("severityLevel", "HIGH"),
            actor,
            v3.id()
        );

        TicketInfo historical = this.tickets.read(historicalId, ALLOW_ALL, QueryFieldContext.empty());
        TicketInfo current = this.tickets.read(currentId, ALLOW_ALL, QueryFieldContext.empty());

        assertThat(historical.templateVersionId()).isEqualTo(v1.id());
        assertThat(historical.customFields()).containsEntry("severity", "legacy").containsEntry("note", "keep me");
        assertThat(current.templateVersionId()).isEqualTo(v3.id());
        assertThat(current.customFields())
            .containsEntry("severityLevel", "HIGH")
            .doesNotContainKeys("note", "severity");
    }

    private TicketTemplateInfo activateWhenReleased(
        UUID typeId,
        UUID versionId,
        CountDownLatch ready,
        CountDownLatch go
    ) {
        ready.countDown();
        await(go);
        return this.ticketTypes.createTemplate(typeId, versionId, List.of());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", exception);
        }
    }

    private TicketFieldSpec field(String key, String label, TicketFieldType type, boolean required) {
        return new TicketFieldSpec(this.fieldService.create(key, label, type).id(), null, required, Map.of());
    }

    @Test
    @DisplayName("concurrent field registrations cannot assign two identities to one key")
    void shouldRejectConcurrentDuplicateFieldRegistration() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> registerWhenReleased(ready, go, TicketFieldType.STRING));
            Future<Boolean> second = executor.submit(() -> registerWhenReleased(ready, go, TicketFieldType.NUMBER));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                go.countDown();
            }
            assertThat(
                List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(true, false);
        }
        assertThat(
            this.fieldService
                .list()
                .stream()
                .filter(field -> field.key().equals("concurrentField"))
        ).hasSize(1);
    }

    private boolean registerWhenReleased(CountDownLatch ready, CountDownLatch go, TicketFieldType type) {
        ready.countDown();
        await(go);
        try {
            this.fieldService.create("concurrentField", "Concurrent field", type);
            return true;
        } catch (TicketFieldException exception) {
            assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT);
            return false;
        }
    }

    /// Verifies the losing creation rolls back its schema and reports a domain conflict.
    @Test
    @DisplayName("concurrent duplicate Type creation commits one complete schema")
    void shouldRejectConcurrentDuplicateTypeCreation() throws Exception {
        String key = "duplicate-type-" + UUID.randomUUID();
        Long before = this.jdbc.queryForObject("select count(*) from eav_schema_definitions", Long.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> first = executor.submit(() -> createTypeWhenReleased(key, ready, go));
            Future<Boolean> second = executor.submit(() -> createTypeWhenReleased(key, ready, go));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                go.countDown();
            }
            assertThat(
                List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
            ).containsExactlyInAnyOrder(true, false);
        }
        assertThat(
            this.jdbc.queryForObject("select count(*) from ticket_types where type_key = ?", Long.class, key)
        ).isEqualTo(1L);
        assertThat(this.jdbc.queryForObject("select count(*) from eav_schema_definitions", Long.class)).isEqualTo(
            Objects.requireNonNull(before) + 1
        );
        assertThat(
            this.ticketTypes
                .list()
                .stream()
                .filter(type -> type.key().equals(key))
        )
            .singleElement()
            .satisfies(type -> assertThat(type.currentTemplate().status()).isEqualTo(TicketTemplateStatus.ACTIVE));
    }

    private boolean createTypeWhenReleased(String key, CountDownLatch ready, CountDownLatch go) {
        ready.countDown();
        await(go);
        try {
            this.ticketTypes.create(key, "Concurrent Type", List.of());
            return true;
        } catch (TicketTypeException exception) {
            assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT);
            return false;
        }
    }

    /// Verifies concurrent creation is pinned to the old schema or rejected as stale, never reinterpreted.
    @Test
    @DisplayName("serializes Ticket creation against template activation")
    void shouldSerializeCreationAgainstActivation() throws Exception {
        UUID actor = insertUser("actual-create-race");
        var type = this.ticketTypes.create("actual-create-race", "Race", List.of());
        UUID previous = type.currentTemplate().id();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<TicketTemplateInfo> activation = executor.submit(() ->
                activateWhenReleased(type.id(), previous, ready, go)
            );
            Future<Optional<UUID>> creation = executor.submit(() -> {
                ready.countDown();
                await(go);
                try {
                    return Optional.of(
                        this.tickets.create(type.id(), "Racing Ticket", "OPEN", Map.of(), actor, previous)
                    );
                } catch (TicketTypeException exception) {
                    assertThat(exception.type()).isEqualTo(DomainFailureType.CONFLICT);
                    return Optional.empty();
                }
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                go.countDown();
            }
            var next = activation.get(10, TimeUnit.SECONDS);
            var created = creation.get(10, TimeUnit.SECONDS);
            assertThat(this.ticketTypes.get(type.id()).currentTemplate().id()).isEqualTo(next.id());
            created.ifPresent(id ->
                assertThat(this.tickets.read(id, ALLOW_ALL, QueryFieldContext.empty()).templateVersionId()).isEqualTo(
                    previous
                )
            );
            assertThat(
                this.jdbc.queryForObject("select count(*) from tickets where ticket_type_id = ?", Long.class, type.id())
            ).isEqualTo(created.isPresent() ? 1L : 0L);
        }
    }

    /// Verifies the active snapshot is loaded with a constant number of SQL statements.
    @Test
    @DisplayName("loads Ticket Types and their current fields in three queries")
    void shouldBulkLoadCurrentTemplates() {
        var field = field("bulkField", "Bulk field", TicketFieldType.STRING, false);
        var first = this.ticketTypes.create("bulk-first", "First", List.of(field));
        this.ticketTypes.createTemplate(first.id(), first.currentTemplate().id(), List.of());
        for (int index = 0; index < 8; index++) {
            this.ticketTypes.create("bulk-" + index, "Bulk " + index, List.of(field));
        }
        var statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            long before = statistics.getPrepareStatementCount();
            var listed = this.ticketTypes.list();
            assertThat(statistics.getPrepareStatementCount() - before).isEqualTo(3);
            assertThat(listed.stream().filter(type -> type.key().matches("bulk-[0-7]")))
                .hasSize(8)
                .allSatisfy(type -> assertThat(type.currentTemplate().fields()).hasSize(1));
            assertThat(listed.stream().filter(type -> type.id().equals(first.id())))
                .singleElement()
                .satisfies(type -> assertThat(type.currentTemplate().fields()).isEmpty());
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    /// Verifies invalid initial fields roll back the complete new Type.
    @Test
    @DisplayName("rolls back invalid initial schema without leaving an orphan definition")
    void shouldRollbackInvalidInitialSchema() {
        var before = this.jdbc.queryForObject("select count(*) from eav_schema_definitions", Long.class);
        assertThatThrownBy(() ->
            this.ticketTypes.create(
                "invalid-initial",
                "Invalid",
                List.of(new TicketFieldSpec(UUID.randomUUID(), null, false, Map.of()))
            )
        ).isInstanceOf(TicketFieldException.class);
        assertThat(this.jdbc.queryForObject("select count(*) from eav_schema_definitions", Long.class)).isEqualTo(
            before
        );
        assertThat(
            this.jdbc.queryForObject("select count(*) from ticket_types where type_key = 'invalid-initial'", Long.class)
        ).isZero();
    }

    /// Verifies equivalent numeric values and missing optional fields do not rewrite Ticket or EAV rows.
    @Test
    @DisplayName("no-op PATCH preserves timestamps, optimistic version and EAV rows")
    void shouldNotWriteEquivalentPatch() {
        var type = this.ticketTypes.create(
            "noop-patch",
            "No-op",
            List.of(
                field("noopScore", "Score", TicketFieldType.NUMBER, false),
                field("noopNote", "Note", TicketFieldType.STRING, false)
            )
        );
        UUID id = this.tickets.create(
            type.id(),
            "Same title",
            "OPEN",
            Map.of("noopScore", 2),
            insertUser("noop"),
            null
        );
        String sql = """
        select t.updated_at, t.version, v.xmin::text as row_revision
        from tickets t join eav_field_values v on v.schema_instance_id = t.schema_instance_id
        where t.id = ?
        """;
        var before = this.jdbc.queryForMap(sql, id);
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        values.put("noopScore", new BigDecimal("2.00"));
        values.put("noopNote", null);
        this.tickets.update(id, new TicketPatch("Same title", "OPEN", values), ALLOW_ALL, QueryFieldContext.empty());
        assertThat(this.jdbc.queryForMap(sql, id)).isEqualTo(before);

        this.tickets.update(
            id,
            new TicketPatch(null, null, Map.of("noopScore", 3)),
            ALLOW_ALL,
            QueryFieldContext.empty()
        );
        assertThat(this.jdbc.queryForMap(sql, id)).isNotEqualTo(before);
        assertThat(this.tickets.read(id, ALLOW_ALL, QueryFieldContext.empty()).customFields()).containsEntry(
            "noopScore",
            new BigDecimal("3")
        );
    }

    private UUID insertUser(String username) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "INSERT INTO users (id, username, first_name, last_name, status) VALUES (?, ?, 'Ticket', 'Actor', 'ACTIVE')",
            id,
            username + '-' + id
        );
        return id;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackages = { "io.taskmigo.eav", "io.taskmigo.ticket" })
    @EnableJpaRepositories(basePackages = { "io.taskmigo.eav", "io.taskmigo.ticket" })
    @ComponentScan(
        basePackages = { "io.taskmigo.eav", "io.taskmigo.ticket" },
        excludeFilters = @Filter(type = FilterType.ANNOTATION, classes = SpringBootConfiguration.class)
    )
    static class TestApplication {}
}

package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TicketService tickets;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    TicketConcurrencyHistoryIntegrationTest(
        TicketTypeService ticketTypes,
        TicketService tickets,
        JdbcTemplate jdbc,
        PlatformTransactionManager transactionManager
    ) {
        this.ticketTypes = ticketTypes;
        this.tickets = tickets;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    @Test
    @DisplayName("publish racing with create never changes the schema version used to validate the persisted Ticket")
    void shouldPersistExactlyTheSchemaVersionUsedByConcurrentCreate() throws Exception {
        UUID actor = insertUser("publish-create-race");
        UUID typeId = this.ticketTypes.create("publish-create-race", "Publish create race");
        TicketTemplateInfo v1Draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(
            typeId,
            v1Draft.id(),
            List.of(new TicketFieldSpec(UUID.randomUUID(), "legacy", "Legacy", TicketFieldType.STRING, true, Map.of()))
        );
        TicketTemplateInfo v1 = this.ticketTypes.publishTemplate(typeId, v1Draft.id());
        TicketTemplateInfo v2Draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(
            typeId,
            v2Draft.id(),
            List.of(new TicketFieldSpec(UUID.randomUUID(), "next", "Next", TicketFieldType.STRING, true, Map.of()))
        );
        UUID schemaDefinitionId = Objects.requireNonNull(
            this.jdbc.queryForObject("SELECT schema_definition_id FROM ticket_types WHERE id = ?", UUID.class, typeId)
        );
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> locker = executor.submit(() -> {
                new TransactionTemplate(this.transactionManager).executeWithoutResult(status -> {
                    this.jdbc.queryForObject(
                        "SELECT id FROM eav_schema_definitions WHERE id = ? FOR UPDATE",
                        UUID.class,
                        schemaDefinitionId
                    );
                    locked.countDown();
                    await(release);
                });
            });
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<TicketTemplateInfo> publish = executor.submit(() ->
                this.ticketTypes.publishTemplate(typeId, v2Draft.id())
            );

            UUID ticketId = this.tickets.create(
                typeId,
                "Race winner",
                "OPEN",
                Map.of("legacy", "validated-by-v1"),
                actor
            );
            release.countDown();
            TicketTemplateInfo v2 = publish.get(10, TimeUnit.SECONDS);
            locker.get(10, TimeUnit.SECONDS);

            TicketInfo ticket = this.tickets.read(ticketId, ALLOW_ALL, QueryFieldContext.empty());
            assertThat(ticket.templateVersionId()).isEqualTo(v1.id()).isNotEqualTo(v2.id());
            assertThat(ticket.customFields()).containsExactly(Map.entry("legacy", "validated-by-v1"));
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("concurrent publication of the same draft serializes to exactly one successful publish")
    void shouldSerializeDoublePublish() throws Exception {
        UUID typeId = this.ticketTypes.create("double-publish", "Double publish");
        TicketTemplateInfo draft = this.ticketTypes.createTemplate(typeId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<TicketTemplateInfo> first = executor.submit(() ->
                publishWhenReleased(typeId, draft.id(), ready, go)
            );
            Future<TicketTemplateInfo> second = executor.submit(() ->
                publishWhenReleased(typeId, draft.id(), ready, go)
            );
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int success = 0;
            int stateConflict = 0;
            for (Future<TicketTemplateInfo> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    success++;
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof IllegalStateException) {
                        stateConflict++;
                    } else {
                        throw exception;
                    }
                }
            }
            assertThat(success).isEqualTo(1);
            assertThat(stateConflict).isEqualTo(1);
        } finally {
            go.countDown();
        }
    }

    @Test
    @DisplayName("historical values survive label rename field removal type replacement and retirement")
    void shouldPreserveHistoricalCorpusAcrossSchemaEvolution() {
        UUID actor = insertUser("historical-corpus");
        UUID typeId = this.ticketTypes.create("historical-corpus", "Historical corpus");
        UUID severityV1Id = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        TicketTemplateInfo v1Draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(
            typeId,
            v1Draft.id(),
            List.of(
                new TicketFieldSpec(severityV1Id, "severity", "Severity", TicketFieldType.STRING, true, Map.of()),
                new TicketFieldSpec(noteId, "note", "Note", TicketFieldType.STRING, false, Map.of())
            )
        );
        TicketTemplateInfo v1 = this.ticketTypes.publishTemplate(typeId, v1Draft.id());
        UUID historicalId = this.tickets.create(
            typeId,
            "Historical",
            "OPEN",
            Map.of("severity", "legacy", "note", "keep me"),
            actor
        );

        TicketTemplateInfo v2Draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(
            typeId,
            v2Draft.id(),
            List.of(
                new TicketFieldSpec(severityV1Id, "severity", "Impact level", TicketFieldType.STRING, true, Map.of())
            )
        );
        TicketTemplateInfo v2 = this.ticketTypes.publishTemplate(typeId, v2Draft.id());

        TicketTemplateInfo v3Draft = this.ticketTypes.createTemplate(typeId);
        UUID severityEnumId = UUID.randomUUID();
        this.ticketTypes.replaceTemplate(
            typeId,
            v3Draft.id(),
            List.of(
                new TicketFieldSpec(
                    severityEnumId,
                    "severity",
                    "Impact level",
                    TicketFieldType.ENUM,
                    true,
                    Map.of("options", List.of("HIGH", "LOW"))
                )
            )
        );
        TicketTemplateInfo v3 = this.ticketTypes.publishTemplate(typeId, v3Draft.id());
        UUID currentId = this.tickets.create(typeId, "Current", "OPEN", Map.of("severity", "HIGH"), actor);
        this.ticketTypes.retireTemplate(typeId, v1.id());
        this.ticketTypes.retireTemplate(typeId, v2.id());

        TicketInfo historical = this.tickets.read(historicalId, ALLOW_ALL, QueryFieldContext.empty());
        TicketInfo current = this.tickets.read(currentId, ALLOW_ALL, QueryFieldContext.empty());

        assertThat(historical.templateVersionId()).isEqualTo(v1.id());
        assertThat(historical.customFields()).containsEntry("severity", "legacy").containsEntry("note", "keep me");
        assertThat(current.templateVersionId()).isEqualTo(v3.id());
        assertThat(current.customFields()).containsEntry("severity", "HIGH").doesNotContainKey("note");
    }

    private TicketTemplateInfo publishWhenReleased(
        UUID typeId,
        UUID versionId,
        CountDownLatch ready,
        CountDownLatch go
    ) {
        ready.countDown();
        await(go);
        return this.ticketTypes.publishTemplate(typeId, versionId);
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

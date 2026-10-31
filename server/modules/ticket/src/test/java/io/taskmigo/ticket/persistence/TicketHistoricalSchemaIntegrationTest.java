package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.support.TestTicketAuthorization;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Import(TestTicketAuthorization.class)
@SpringBootTest(classes = TicketHistoricalSchemaIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class TicketHistoricalSchemaIntegrationTest {

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
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;

    TicketHistoricalSchemaIntegrationTest(
        TicketTypeService ticketTypes,
        TicketService tickets,
        EntityManagerFactory entityManagerFactory,
        JdbcTemplate jdbc
    ) {
        this.ticketTypes = ticketTypes;
        this.tickets = tickets;
        this.entityManagerFactory = entityManagerFactory;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("keeps a Ticket pinned to its historical template after a newer template is published")
    void shouldKeepHistoricalTemplateIdentity() {
        UUID actor = insertUser("historical-ticket-actor");
        UUID typeId = this.ticketTypes.create("historical", "Historical");
        TicketTemplateInfo v1Draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(
            typeId,
            v1Draft.id(),
            List.of(
                new TicketFieldSpec(UUID.randomUUID(), "severity", "Severity", TicketFieldType.STRING, false, Map.of())
            )
        );
        TicketTemplateInfo v1 = this.ticketTypes.publishTemplate(typeId, v1Draft.id());
        UUID ticketId = this.tickets.create(typeId, "Legacy", "OPEN", Map.of("severity", "HIGH"), actor);

        TicketTemplateInfo v2Draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(
            typeId,
            v2Draft.id(),
            List.of(new TicketFieldSpec(UUID.randomUUID(), "impact", "Impact", TicketFieldType.STRING, false, Map.of()))
        );
        this.ticketTypes.publishTemplate(typeId, v2Draft.id());

        TicketInfo historical = this.tickets.read(ticketId, TestTicketAuthorization.context(ALLOW_ALL));

        assertThat(historical.templateVersionId()).isEqualTo(v1.id());
        assertThat(historical.customFields()).containsEntry("severity", "HIGH").doesNotContainKey("impact");
    }

    @Test
    @DisplayName("refuses new Ticket creation after its Ticket Type is retired")
    void shouldRejectCreationForRetiredTicketType() {
        UUID actor = insertUser("retired-ticket-actor");
        UUID typeId = this.ticketTypes.create("retired", "Retired");
        TicketTemplateInfo draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.publishTemplate(typeId, draft.id());
        this.ticketTypes.update(typeId, "Retired", TicketTypeStatus.RETIRED);

        assertThatThrownBy(() ->
            this.tickets.create(typeId, "No longer allowed", "OPEN", Map.of(), actor)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("detects concurrent Ticket updates through JPA optimistic locking")
    void shouldDetectOptimisticLockConflict() {
        UUID actor = insertUser("optimistic-ticket-actor");
        UUID typeId = this.ticketTypes.create("optimistic", "Optimistic");
        TicketTemplateInfo draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.publishTemplate(typeId, draft.id());
        UUID ticketId = this.tickets.create(typeId, "Original", "OPEN", Map.of(), actor);

        EntityManager first = this.entityManagerFactory.createEntityManager();
        EntityManager second = this.entityManagerFactory.createEntityManager();
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            TicketEntity firstCopy = first.find(TicketEntity.class, ticketId);
            TicketEntity secondCopy = second.find(TicketEntity.class, ticketId);
            Instant now = Instant.now();
            firstCopy.updateCommonFields("First writer", "OPEN", now);
            secondCopy.updateCommonFields("Second writer", "OPEN", now.plusMillis(1));

            first.getTransaction().commit();

            assertThatThrownBy(() -> second.getTransaction().commit()).isInstanceOfAny(
                RollbackException.class,
                OptimisticLockException.class
            );
        } finally {
            if (first.getTransaction().isActive()) {
                first.getTransaction().rollback();
            }
            if (second.getTransaction().isActive()) {
                second.getTransaction().rollback();
            }
            first.close();
            second.close();
        }
    }

    private UUID insertUser(String username) {
        UUID id = UUID.randomUUID();
        this.jdbc.update(
            "INSERT INTO users (id, username, first_name, last_name, status) VALUES (?, ?, 'Ticket', 'Actor', 'ACTIVE')",
            id,
            username
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

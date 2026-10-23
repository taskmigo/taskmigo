package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.FilterByException;
import io.taskmigo.query.ListQueryRequest;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.support.TestTicketAuthorization;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@SpringBootTest(classes = TicketQueryPersistenceIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class TicketQueryPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final TicketTypeService ticketTypes;
    private final TicketService tickets;
    private final TicketQueryFieldCatalog fields;
    private final ListTicketsQuerySchema listSchema;
    private final JdbcTemplate jdbc;

    TicketQueryPersistenceIntegrationTest(
        TicketTypeService ticketTypes,
        TicketService tickets,
        TicketQueryFieldCatalog fields,
        ListTicketsQuerySchema listSchema,
        JdbcTemplate jdbc
    ) {
        this.ticketTypes = ticketTypes;
        this.tickets = tickets;
        this.fields = fields;
        this.listSchema = listSchema;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("typed EAV filter and Object Authorization are combined before database pagination")
    void shouldFilterAndAuthorizeBeforePagination() {
        UUID actor = insertUser("query-auth-actor");
        UUID typeId = createType(
            "query-auth",
            List.of(new TicketFieldSpec(UUID.randomUUID(), "score", "Score", TicketFieldType.NUMBER, true, Map.of()))
        );
        for (int score = 1; score <= 5; score++) {
            this.tickets.create(typeId, "Ticket " + score, "OPEN", Map.of("score", score), actor);
        }
        QueryFieldContext context = this.fields.forType(typeId);
        ListQueryRequest query = new ListQueryRequest("object.score >= 2", List.of("score"));
        ObjectAuthorizationPredicate<TicketInfo> authorization = ObjectAuthorizationPredicateModels.from(
            this.listSchema,
            context,
            new ObjectAuthorizationExpression.Binary(
                ObjectAuthorizationExpression.BinaryOperator.GREATER_OR_EQUAL,
                new ObjectAuthorizationExpression.Reference("object", List.of("score")),
                new ObjectAuthorizationExpression.Literal(4)
            )
        );

        OffsetPage<TicketInfo> page = this.tickets.list(
            1,
            2,
            typeId,
            query,
            TestTicketAuthorization.context(authorization)
        );

        assertThat(page.totalItems()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items())
            .extracting(ticket -> ticket.customFields().get("score"))
            .containsExactly(new BigDecimal("4"), new BigDecimal("5"));
    }

    @Test
    @DisplayName("typed custom sorting keeps missing values last without adding a tie breaker")
    void shouldSortTypedValuesWithMissingLastWithoutImplicitTieBreaker() {
        UUID actor = insertUser("query-sort-actor");
        UUID typeId = createType(
            "query-sort",
            List.of(
                new TicketFieldSpec(UUID.randomUUID(), "dueDate", "Due date", TicketFieldType.DATE, false, Map.of())
            )
        );
        UUID late = this.tickets.create(
            typeId,
            "Late",
            "OPEN",
            Map.of("dueDate", LocalDate.parse("2026-10-20")),
            actor
        );
        UUID missing = this.tickets.create(typeId, "Missing", "OPEN", Map.of(), actor);
        UUID firstTie = this.tickets.create(
            typeId,
            "Tie A",
            "OPEN",
            Map.of("dueDate", LocalDate.parse("2026-10-10")),
            actor
        );
        UUID secondTie = this.tickets.create(
            typeId,
            "Tie B",
            "OPEN",
            Map.of("dueDate", LocalDate.parse("2026-10-10")),
            actor
        );
        QueryFieldContext context = this.fields.forType(typeId);
        ListQueryRequest query = new ListQueryRequest(null, List.of("dueDate"));
        ObjectAuthorizationPredicate<TicketInfo> allow = ObjectAuthorizationPredicateModels.constant(
            this.listSchema,
            context,
            true
        );

        OffsetPage<TicketInfo> page = this.tickets.list(1, 10, typeId, query, TestTicketAuthorization.context(allow));

        assertThat(page.items()).hasSize(4);
        assertThat(page.items().subList(0, 2))
            .extracting(TicketInfo::id)
            .containsExactlyInAnyOrder(firstTie, secondTie);
        assertThat(page.items().get(2).id()).isEqualTo(late);
        assertThat(page.items().get(3).id()).isEqualTo(missing);
    }

    @Test
    @DisplayName("unscoped ambiguous custom fields fail closed while an explicit Ticket Type scope remains queryable")
    void shouldFailClosedGloballyAndAllowTypeScopedQuery() {
        UUID actor = insertUser("query-ambiguity-actor");
        UUID firstType = createType(
            "query-ambiguity-a",
            List.of(
                new TicketFieldSpec(UUID.randomUUID(), "customerTier", "Tier", TicketFieldType.NUMBER, false, Map.of())
            )
        );
        createType(
            "query-ambiguity-b",
            List.of(
                new TicketFieldSpec(UUID.randomUUID(), "customerTier", "Tier", TicketFieldType.NUMBER, false, Map.of())
            )
        );
        this.tickets.create(firstType, "Scoped", "OPEN", Map.of("customerTier", 3), actor);
        QueryFieldContext global = this.fields.global();
        QueryFieldContext scoped = this.fields.forType(firstType);

        assertThatThrownBy(() ->
            new FilterByCompiler().compile(this.listSchema, global, "object.customerTier >= 2")
        ).isInstanceOf(FilterByException.class);
        assertThat(
            new FilterByCompiler().compile(this.listSchema, scoped, "object.customerTier >= 2").isAlwaysFalse()
        ).isFalse();
    }

    private UUID createType(String key, List<TicketFieldSpec> fields) {
        UUID typeId = this.ticketTypes.create(key, key);
        TicketTemplateInfo draft = this.ticketTypes.createTemplate(typeId);
        this.ticketTypes.replaceTemplate(typeId, draft.id(), fields);
        this.ticketTypes.publishTemplate(typeId, draft.id());
        return typeId;
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

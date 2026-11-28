package io.taskmigo.ticket.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.model.ObjectAuthorizationPredicateModels;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.FilterByCompiler;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySort;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

@SpringBootTest(classes = TicketQueryPersistenceIntegrationTest.TestApplication.class)
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class TicketQueryPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final TicketTypeService ticketTypes;
    private final TicketFieldService fieldService;
    private final TicketService tickets;
    private final TicketQueryFieldCatalog fields;
    private final ListTicketsQuerySchema listSchema;
    private final JdbcTemplate jdbc;

    TicketQueryPersistenceIntegrationTest(
        TicketTypeService ticketTypes,
        TicketFieldService fieldService,
        TicketService tickets,
        TicketQueryFieldCatalog fields,
        ListTicketsQuerySchema listSchema,
        JdbcTemplate jdbc
    ) {
        this.ticketTypes = ticketTypes;
        this.fieldService = fieldService;
        this.tickets = tickets;
        this.fields = fields;
        this.listSchema = listSchema;
        this.jdbc = jdbc;
    }

    @Test
    @DisplayName("typed EAV filter and Object Authorization are combined before database pagination")
    void shouldFilterAndAuthorizeBeforePagination() {
        UUID actor = insertUser("query-auth-actor");
        UUID typeId = createType("query-auth", List.of(field("score", "Score", TicketFieldType.NUMBER, true)));
        for (int score = 1; score <= 5; score++) {
            this.tickets.create(typeId, "Ticket " + score, "OPEN", Map.of("score", score), actor, null);
        }
        QueryFieldContext context = this.fields.snapshot();
        ListQuerySpec<TicketInfo> query = new ListQuerySpec<>(
            compile(context, "object.score >= 2"),
            context,
            List.of(new QuerySort(QueryPath.of("score"), QuerySort.Direction.ASC))
        );
        ObjectAuthorizationPredicate<TicketInfo> authorization = ObjectAuthorizationPredicateModels.from(
            this.listSchema,
            context,
            new ObjectAuthorizationExpression.Binary(
                ObjectAuthorizationExpression.BinaryOperator.GREATER_OR_EQUAL,
                new ObjectAuthorizationExpression.Reference("object", List.of("score")),
                new ObjectAuthorizationExpression.Literal(4)
            )
        );

        OffsetPage<TicketInfo> page = this.tickets.list(1, 2, typeId, query, authorization);

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
        UUID typeId = createType("query-sort", List.of(field("dueDate", "Due date", TicketFieldType.DATE, false)));
        UUID late = this.tickets.create(
            typeId,
            "Late",
            "OPEN",
            Map.of("dueDate", LocalDate.parse("2026-10-20")),
            actor,
            null
        );
        UUID missing = this.tickets.create(typeId, "Missing", "OPEN", Map.of(), actor, null);
        UUID firstTie = this.tickets.create(
            typeId,
            "Tie A",
            "OPEN",
            Map.of("dueDate", LocalDate.parse("2026-10-10")),
            actor,
            null
        );
        UUID secondTie = this.tickets.create(
            typeId,
            "Tie B",
            "OPEN",
            Map.of("dueDate", LocalDate.parse("2026-10-10")),
            actor,
            null
        );
        QueryFieldContext context = this.fields.snapshot();
        ListQuerySpec<TicketInfo> query = new ListQuerySpec<>(
            compile(context, null),
            context,
            List.of(new QuerySort(QueryPath.of("dueDate"), QuerySort.Direction.ASC))
        );
        ObjectAuthorizationPredicate<TicketInfo> allow = ObjectAuthorizationPredicateModels.constant(
            this.listSchema,
            context,
            true
        );

        OffsetPage<TicketInfo> page = this.tickets.list(1, 10, typeId, query, allow);

        assertThat(page.items()).hasSize(4);
        assertThat(page.items().subList(0, 2))
            .extracting(TicketInfo::id)
            .containsExactlyInAnyOrder(firstTie, secondTie);
        assertThat(page.items().get(2).id()).isEqualTo(late);
        assertThat(page.items().get(3).id()).isEqualTo(missing);
    }

    @Test
    @DisplayName("shared fields preserve numeric query and authorization semantics across types and retired versions")
    void shouldQueryAcrossTypesWhenFieldIdentityIsShared() {
        UUID actor = insertUser("query-shared-field-actor");
        UUID fieldId = this.fieldService.create("customerTier", "Tier", TicketFieldType.NUMBER).id();
        TicketFieldSpec spec = new TicketFieldSpec(fieldId, null, false, Map.of());
        UUID firstType = createType("query-shared-a", List.of(spec));
        UUID secondType = createType("query-shared-b", List.of(spec));
        UUID oldVersion = this.ticketTypes.get(firstType).currentTemplate().id();
        UUID high = this.tickets.create(firstType, "High", "SHARED_QUERY", Map.of("customerTier", 10), actor, null);
        this.ticketTypes.createTemplate(firstType, oldVersion, List.of(spec));
        UUID low = this.tickets.create(firstType, "Low", "SHARED_QUERY", Map.of("customerTier", 2), actor, null);
        UUID highest = this.tickets.create(
            secondType,
            "Highest",
            "SHARED_QUERY",
            Map.of("customerTier", 20),
            actor,
            null
        );
        UUID missingType = createType("query-shared-missing", List.of());
        UUID missing = this.tickets.create(missingType, "Missing", "SHARED_QUERY", Map.of(), actor, null);
        this.fieldService.update(fieldId, null, TicketFieldStatus.ARCHIVED);

        QueryFieldContext context = this.fields.snapshot();
        ObjectAuthorizationPredicate<TicketInfo> allow = ObjectAuthorizationPredicateModels.constant(
            this.listSchema,
            context,
            true
        );

        ListQuerySpec<TicketInfo> ascending = new ListQuerySpec<>(
            compile(context, "object.status == \"SHARED_QUERY\""),
            context,
            List.of(new QuerySort(QueryPath.of("customerTier"), QuerySort.Direction.ASC))
        );
        ListQuerySpec<TicketInfo> descending = new ListQuerySpec<>(
            ascending.predicate(),
            context,
            List.of(new QuerySort(QueryPath.of("customerTier"), QuerySort.Direction.DESC))
        );
        ListQuerySpec<TicketInfo> unequal = new ListQuerySpec<>(
            compile(context, "object.customerTier != 2"),
            context,
            ascending.sorting()
        );
        assertThat(this.tickets.list(1, 10, null, ascending, allow).items())
            .extracting(TicketInfo::id)
            .containsExactly(low, high, highest, missing);
        assertThat(this.tickets.list(1, 10, null, descending, allow).items())
            .extracting(TicketInfo::id)
            .containsExactly(highest, high, low, missing);
        assertThat(this.tickets.list(1, 10, null, unequal, allow).items())
            .extracting(TicketInfo::id)
            .containsExactly(high, highest);

        ObjectAuthorizationPredicate<TicketInfo> authorized = ObjectAuthorizationPredicateModels.from(
            this.listSchema,
            context,
            new ObjectAuthorizationExpression.Binary(
                ObjectAuthorizationExpression.BinaryOperator.GREATER_OR_EQUAL,
                new ObjectAuthorizationExpression.Reference("object", List.of("customerTier")),
                new ObjectAuthorizationExpression.Literal(10)
            )
        );
        OffsetPage<TicketInfo> first = this.tickets.list(1, 1, null, ascending, authorized);
        OffsetPage<TicketInfo> second = this.tickets.list(2, 1, null, ascending, authorized);
        assertThat(first.totalItems()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items()).extracting(TicketInfo::id).containsExactly(high);
        assertThat(second.items()).extracting(TicketInfo::id).containsExactly(highest);
    }

    private UUID createType(String key, List<TicketFieldSpec> fields) {
        return this.ticketTypes.create(key, key, fields).id();
    }

    private QueryPredicate<TicketInfo> compile(QueryFieldContext context, @Nullable String source) {
        return (QueryPredicate<TicketInfo>) new FilterByCompiler().compile(this.listSchema, context, source);
    }

    private TicketFieldSpec field(String key, String label, TicketFieldType type, boolean required) {
        return new TicketFieldSpec(this.fieldService.create(key, label, type).id(), null, required, Map.of());
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

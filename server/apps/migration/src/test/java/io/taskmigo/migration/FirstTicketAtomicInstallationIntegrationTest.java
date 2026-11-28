package io.taskmigo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.application.api.RequestAuthorization;
import io.taskmigo.migration.bootstrap.MigrationResourceLoader;
import io.taskmigo.migration.installation.MigrationInstaller;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketPatch;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.ticket.type.application.api.TicketTypeException;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
        "TM_SYSTEM_PASSWORD=integration-password",
        "TM_BROWSER_CLIENT_SECRET=browser-integration-secret",
        "TM_BROWSER_HOST_NAME=http://localhost:3000",
        "TM_BROWSER_AUTHENTICATION_ENABLED=true",
    }
)
@Import(PostgresTestConfiguration.class)
@MockitoBean(name = "migrationRunner", types = ApplicationRunner.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FirstTicketAtomicInstallationIntegrationTest {

    private final MigrationInstaller migration;
    private final MigrationResourceLoader resources;
    private final TicketTypeService types;
    private final TicketService tickets;
    private final TicketQueryFieldContexts fields;
    private final RequestAuthorization requestAuthorization;
    private final ObjectAuthorization objectAuthorization;
    private final List<QuerySchemaView> schemas;
    private final JdbcTemplate jdbc;

    FirstTicketAtomicInstallationIntegrationTest(
        MigrationInstaller migration,
        MigrationResourceLoader resources,
        TicketTypeService types,
        TicketService tickets,
        TicketQueryFieldContexts fields,
        RequestAuthorization requestAuthorization,
        ObjectAuthorization objectAuthorization,
        List<QuerySchemaView> schemas,
        JdbcTemplate jdbc
    ) {
        this.migration = migration;
        this.resources = resources;
        this.types = types;
        this.tickets = tickets;
        this.fields = fields;
        this.requestAuthorization = requestAuthorization;
        this.objectAuthorization = objectAuthorization;
        this.schemas = schemas;
        this.jdbc = jdbc;
    }

    /// Verifies a failed seed rolls back both mandatory provisioning and earlier optional writes.
    @Test
    @DisplayName("rolls back the entire first installation when the seed Type conflicts")
    void shouldRollbackFailedSeed() {
        var existing = this.types.create("task", "Existing Type", List.of());
        assertThatThrownBy(() -> this.migration.install(this.resources.load(), true)).isInstanceOf(
            TicketTypeException.class
        );

        assertThat(this.jdbc.queryForObject("select count(*) from users", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from roles", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from statements", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from oauth2_registered_client", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from audit_logs", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from ticket_field_registry", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from tickets", Long.class)).isZero();
        assertThat(this.types.list()).containsExactly(existing);
    }

    /// Verifies concurrent fresh installs converge and both privileged principals can edit the actual seed.
    @Test
    @DisplayName("concurrent fresh installations create one editable example Ticket")
    void shouldInitializeExactlyOnceUnderConcurrency() throws Exception {
        var plan = this.resources.load();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                this.migration.install(plan, true);
                return true;
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                this.migration.install(plan, true);
                return true;
            });
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            assertThat(first.get(60, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(this.jdbc.queryForObject("select count(*) from tickets", Long.class)).isEqualTo(1L);
        assertThat(this.jdbc.queryForObject("select count(*) from ticket_types", Long.class)).isEqualTo(1L);
        assertThat(this.jdbc.queryForObject("select count(*) from ticket_field_registry", Long.class)).isEqualTo(1L);
        UUID ticketId = Objects.requireNonNull(this.jdbc.queryForObject("select id from tickets", UUID.class));

        for (String username : List.of("system", "admin")) {
            assertCanUpdateSeed(username, ticketId);
        }

        this.migration.install(plan, true);
        assertThat(
            this.jdbc.queryForObject("select title from tickets where id = ?", String.class, ticketId)
        ).isEqualTo("Edited by admin");
        assertThat(this.jdbc.queryForObject("select count(*) from tickets", Long.class)).isEqualTo(1L);
    }

    private void assertCanUpdateSeed(String username, UUID ticketId) {
        UUID userId = Objects.requireNonNull(
            this.jdbc.queryForObject("select id from users where username = ?", UUID.class, username)
        );
        var decision = this.requestAuthorization.authorize(
            new AuthorizationPrincipal(userId, username),
            new AuthorizationRequest("PATCH", "/api/v0/tickets/" + ticketId, Map.of("ticketId", ticketId.toString()))
        );
        assertThat(decision.granted()).isTrue();
        var schema = this.schemas
            .stream()
            .filter(value -> value.operation().equals("tickets.update"))
            .findFirst()
            .orElseThrow();
        var context = this.fields.snapshot();
        var predicate = this.objectAuthorization.<TicketInfo>authorize(decision.context(), schema, context);
        var updated = this.tickets.update(
            ticketId,
            new TicketPatch("Edited by " + username, null, Map.of()),
            predicate,
            context
        );
        assertThat(updated.title()).isEqualTo("Edited by " + username);
    }
}

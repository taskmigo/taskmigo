package io.taskmigo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.migration.bootstrap.MigrationResourceLoader;
import io.taskmigo.migration.installation.MigrationInstaller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    properties = {
        "TM_SYSTEM_PASSWORD=integration-password",
        "TM_BROWSER_AUTHENTICATION_ENABLED=false",
        "TM_INIT_FIRST_TICKET=false",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class FirstTicketDisabledIntegrationTest {

    private final MigrationInstaller migration;
    private final MigrationResourceLoader resources;
    private final JdbcTemplate jdbc;

    FirstTicketDisabledIntegrationTest(
        MigrationInstaller migration,
        MigrationResourceLoader resources,
        JdbcTemplate jdbc
    ) {
        this.migration = migration;
        this.resources = resources;
        this.jdbc = jdbc;
    }

    /**
     * Verifies opting out is an installation-time decision, not a deferred reconciliation.
     *
     * Given: a fresh database installed with the first Ticket disabled.
     * Expect: mandatory identities exist but example data is absent, even after enabling the option.
     */
    @Test
    @DisplayName("skips all optional seed data and never backfills it")
    void shouldSkipFirstTicketPermanently() {
        assertThat(
            this.jdbc.queryForObject("select count(*) from users where username = 'system'", Long.class)
        ).isEqualTo(1L);
        assertNoExampleData();

        this.migration.install(this.resources.load(), true);

        assertNoExampleData();
    }

    private void assertNoExampleData() {
        assertThat(this.jdbc.queryForObject("select count(*) from tickets", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from ticket_types", Long.class)).isZero();
        assertThat(this.jdbc.queryForObject("select count(*) from ticket_field_registry", Long.class)).isZero();
    }
}

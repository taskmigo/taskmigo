package io.taskmigo.migration.bootstrap;

import io.taskmigo.migration.installation.MigrationInstaller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/// Runs installation reconciliation once when the migration application starts.
@Component
final class MigrationRunner implements ApplicationRunner {

    private final MigrationResourceLoader resources;
    private final MigrationInstaller migration;

    private final boolean initFirstTicket;

    MigrationRunner(
        MigrationResourceLoader resources,
        MigrationInstaller migration,
        @Value("${taskmigo.migration.init-first-ticket}") boolean initFirstTicket
    ) {
        this.resources = resources;
        this.migration = migration;
        this.initFirstTicket = initFirstTicket;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.migration.install(this.resources.load(), this.initFirstTicket);
    }
}

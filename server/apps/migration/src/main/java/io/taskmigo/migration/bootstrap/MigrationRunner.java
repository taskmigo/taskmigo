package io.taskmigo.migration.bootstrap;

import io.taskmigo.migration.application.service.MigrationInstaller;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/// Runs installation reconciliation once when the migration application starts.
@Component
final class MigrationRunner implements ApplicationRunner {

    private final MigrationResourceLoader resources;
    private final MigrationInstaller migration;

    MigrationRunner(MigrationResourceLoader resources, MigrationInstaller migration) {
        this.resources = resources;
        this.migration = migration;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.migration.install(this.resources.load());
    }
}

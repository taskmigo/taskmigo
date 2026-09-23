package io.taskmigo.migration.adapter.in.installation;

import io.taskmigo.migration.application.port.in.InstallationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/// Adapts the one-shot Spring Boot startup trigger to the installation inbound port.
@Component
final class MigrationRunner implements ApplicationRunner {

    private final MigrationResourceLoader resources;
    private final InstallationService installation;

    MigrationRunner(MigrationResourceLoader resources, InstallationService installation) {
        this.resources = resources;
        this.installation = installation;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        this.installation.install(this.resources.load());
    }
}

package io.taskmigo.migration.adapter.out.logging;

import io.taskmigo.migration.application.model.InstallationChange;
import io.taskmigo.migration.application.port.out.InstallationChangePublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/// Publishes committed installation changes as structured ECS-compatible log events.
@Component
final class LoggingInstallationChangePublisher implements InstallationChangePublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingInstallationChangePublisher.class);

    @Override
    public void publish(List<InstallationChange> changes) {
        for (InstallationChange change : changes) {
            if (change.action() == InstallationChange.Action.UNCHANGED) {
                continue;
            }
            LOGGER.atInfo()
                .addKeyValue("event.type", "change")
                .addKeyValue("event.action", change.action().value())
                .addKeyValue("taskmigo.migration.resource.type", change.resourceType())
                .addKeyValue("taskmigo.migration.resource.key", change.resourceKey())
                .log("Migration resource changed");
        }
    }
}

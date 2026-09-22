package io.taskmigo.migration.adapter.in.installation;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/// Publishes managed migration changes as structured ECS-compatible log events.
@Component
final class MigrationChangeLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationChangeLogger.class);

    void log(List<MigrationChange> changes) {
        for (MigrationChange change : changes) {
            if (change.action() == MigrationChange.Action.UNCHANGED) {
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

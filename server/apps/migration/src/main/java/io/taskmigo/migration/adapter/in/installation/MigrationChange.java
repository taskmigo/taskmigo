package io.taskmigo.migration.adapter.in.installation;

/// Represents one managed migration resource change that is safe to publish in an operational log.
record MigrationChange(String resourceType, String resourceKey, Action action) {
    enum Action {
        ADDED("added"),
        UPDATED("updated"),
        REMOVED("removed"),
        UNCHANGED("unchanged");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        String value() {
            return this.value;
        }
    }
}

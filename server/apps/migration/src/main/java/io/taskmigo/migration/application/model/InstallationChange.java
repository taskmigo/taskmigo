package io.taskmigo.migration.application.model;

/// Represents one managed installation resource change that is safe to publish after commit.
public record InstallationChange(String resourceType, String resourceKey, Action action) {

    /// Classifies the persisted effect of one installation reconciliation.
    public enum Action {
        ADDED("added"),
        UPDATED("updated"),
        REMOVED("removed"),
        UNCHANGED("unchanged");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        /// Returns the stable operational event value.
        ///
        /// @return event action value
        public String value() {
            return this.value;
        }
    }
}

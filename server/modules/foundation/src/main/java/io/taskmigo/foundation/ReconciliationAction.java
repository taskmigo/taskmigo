package io.taskmigo.foundation;

/// Identifies the persistent change performed by a reconciliation operation.
public enum ReconciliationAction {
    ADDED("added"),
    UPDATED("updated"),
    REMOVED("removed"),
    UNCHANGED("unchanged");

    private final String value;

    ReconciliationAction(String value) {
        this.value = value;
    }

    /// Returns the lowercase action value used in structured migration logs.
    public String value() {
        return this.value;
    }
}

package io.taskmigo.migration;

import io.taskmigo.foundation.ReconciliationAction;

/// Represents one managed migration resource change that is safe to publish in an operational log.
record MigrationChange(String resourceType, String resourceKey, ReconciliationAction action) {}

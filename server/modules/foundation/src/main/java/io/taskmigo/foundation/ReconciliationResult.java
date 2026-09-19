package io.taskmigo.foundation;

/// Describes the identifier and change action produced by reconciling one managed resource.
public record ReconciliationResult<T>(T id, ReconciliationAction action) {}

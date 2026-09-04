package io.taskmigo.auth.authorization.object;

import java.util.Map;

/// Defines the safe persisted fields available to a resource's database filter.
public interface FilterSchema {
    /// Returns field names and their persisted Java types.
    Map<String, Class<?>> fields();
}

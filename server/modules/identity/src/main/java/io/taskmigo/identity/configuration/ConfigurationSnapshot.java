package io.taskmigo.identity.configuration;

/// Exposes the effective user-manageable Taskmigo application configuration.
public record ConfigurationSnapshot(RetentionConfiguration retention) {
    /// Groups retention settings by resource type.
    public record RetentionConfiguration(RetentionDuration user) {}
}

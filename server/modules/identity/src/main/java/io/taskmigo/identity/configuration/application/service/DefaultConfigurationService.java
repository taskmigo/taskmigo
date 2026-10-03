package io.taskmigo.identity.configuration.application.service;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.ConfigurationSnapshot.RetentionConfiguration;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.configuration.application.port.out.ConfigurationStore;

/// Implements database-backed user-manageable Taskmigo configuration.
public final class DefaultConfigurationService implements ConfigurationService {

    static final String USER_RETENTION_KEY = "retention.user";
    private static final RetentionDuration DEFAULT_USER_RETENTION = RetentionDuration.parse("P30D");

    private final ConfigurationStore configurations;
    private final TransactionRunner transactions;

    public DefaultConfigurationService(ConfigurationStore configurations, TransactionRunner transactions) {
        this.configurations = configurations;
        this.transactions = transactions;
    }

    @Override
    public ConfigurationSnapshot get() {
        return this.transactions.read(this::snapshot);
    }

    @Override
    public ConfigurationSnapshot updateUserRetention(RetentionDuration retention) {
        return this.transactions.write(() -> {
            this.configurations.save(USER_RETENTION_KEY, retention.toString());
            return this.snapshot();
        });
    }

    private ConfigurationSnapshot snapshot() {
        RetentionDuration user = this.configurations
            .find(USER_RETENTION_KEY)
            .map(RetentionDuration::parse)
            .orElse(DEFAULT_USER_RETENTION);
        return new ConfigurationSnapshot(new RetentionConfiguration(user));
    }
}

package io.taskmigo.identity.configuration.application;

import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.ConfigurationSnapshot.RetentionConfiguration;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.api.ConfigurationService;
import org.springframework.transaction.annotation.Transactional;

/// Implements database-backed user-manageable Taskmigo configuration.
@Transactional
public class DefaultConfigurationService implements ConfigurationService {

    static final String USER_RETENTION_KEY = "retention.user";
    private static final RetentionDuration DEFAULT_USER_RETENTION = RetentionDuration.parse("P30D");

    private final ConfigurationStore configurations;

    public DefaultConfigurationService(ConfigurationStore configurations) {
        this.configurations = configurations;
    }

    @Transactional(readOnly = true)
    @Override
    public ConfigurationSnapshot get() {
        return this.snapshot();
    }

    @Override
    public ConfigurationSnapshot updateUserRetention(RetentionDuration retention) {
        this.configurations.save(USER_RETENTION_KEY, retention.toString());
        return this.snapshot();
    }

    private ConfigurationSnapshot snapshot() {
        RetentionDuration user = this.configurations
            .find(USER_RETENTION_KEY)
            .map(RetentionDuration::parse)
            .orElse(DEFAULT_USER_RETENTION);
        return new ConfigurationSnapshot(new RetentionConfiguration(user));
    }
}

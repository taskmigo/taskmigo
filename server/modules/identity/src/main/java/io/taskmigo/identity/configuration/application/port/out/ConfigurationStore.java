package io.taskmigo.identity.configuration.application.port.out;

import java.util.Optional;

/// Persists user-manageable application configuration values by stable key.
public interface ConfigurationStore {

    Optional<String> find(String key);

    void save(String key, String value);
}

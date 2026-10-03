package io.taskmigo.identity.configuration.adapter.out.persistence;

import io.taskmigo.identity.configuration.application.port.out.ConfigurationStore;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/// Adapts database-backed application configuration storage to Spring Data JPA.
@Repository
public class JpaConfigurationStore implements ConfigurationStore {

    private final JpaConfigurationRepository configurations;

    JpaConfigurationStore(JpaConfigurationRepository configurations) {
        this.configurations = configurations;
    }

    @Override
    public Optional<String> find(String key) {
        return this.configurations.findById(key).map(configuration -> configuration.value);
    }

    @Override
    public void save(String key, String value) {
        this.configurations.save(new ConfigurationEntity(key, value));
    }
}

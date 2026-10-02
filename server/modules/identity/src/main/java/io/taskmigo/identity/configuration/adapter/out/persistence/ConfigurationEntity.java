package io.taskmigo.identity.configuration.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "application_configuration")
@SuppressWarnings("NotNullFieldNotInitialized")
class ConfigurationEntity {

    @Id
    @Column(name = "configuration_key", length = 100)
    String key;

    @Column(name = "configuration_value", nullable = false, length = 1000)
    String value;

    protected ConfigurationEntity() {}

    ConfigurationEntity(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

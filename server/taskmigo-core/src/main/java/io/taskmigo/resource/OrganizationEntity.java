package io.taskmigo.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@SuppressWarnings("NotNullFieldNotInitialized")
class OrganizationEntity {

    @Id
    UUID id;

    @Column(name = "organization_key", nullable = false, length = 64)
    String key;

    @Column(nullable = false, length = 200)
    String name;

    protected OrganizationEntity() {}

    OrganizationEntity(UUID id, String key, String name) {
        this.id = id;
        this.key = key;
        this.name = name;
    }

    UUID getId() {
        return this.id;
    }
}

package io.taskmigo.identity.persistence.oauth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "oauth2_registered_client_metadata")
@SuppressWarnings("NotNullFieldNotInitialized")
final class RegisteredClientMetadataEntity {

    @Id
    @Column(name = "registered_client_id", length = 100)
    String registeredClientId;

    @Column(name = "client_type", nullable = false, length = 20)
    String type;

    protected RegisteredClientMetadataEntity() {}
}

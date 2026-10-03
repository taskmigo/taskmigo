package io.taskmigo.identity.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;

/// Maps the library-owned consent key without loading authorities or token data.
@Entity
@Table(name = "oauth2_authorization_consent")
@SuppressWarnings("NotNullFieldNotInitialized")
class OAuthConsentEntity {
    @EmbeddedId
    ConsentId id;

    protected OAuthConsentEntity() {}

    @Embeddable
    record ConsentId(
        @Column(name = "registered_client_id", length = 100) String registeredClientId,
        @Column(name = "principal_name", length = 200) String principalName
    ) implements Serializable {}
}

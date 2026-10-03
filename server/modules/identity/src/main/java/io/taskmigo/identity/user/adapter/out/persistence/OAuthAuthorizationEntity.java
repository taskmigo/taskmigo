package io.taskmigo.identity.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/// Maps only the ownership fields needed to remove library-owned OAuth authorizations.
@Entity
@Table(name = "oauth2_authorization")
@SuppressWarnings("NotNullFieldNotInitialized")
class OAuthAuthorizationEntity {
    @Id
    @Column(length = 100)
    String id;

    @Column(name = "principal_name", nullable = false, length = 200)
    String principalName;

    protected OAuthAuthorizationEntity() {}
}

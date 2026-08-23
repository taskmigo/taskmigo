package io.taskmigo.resource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
@SuppressWarnings("NotNullFieldNotInitialized")
class UserEntity {

    @Id
    UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    OrganizationEntity organization;

    @Column(nullable = false, length = 100)
    String username;

    @Column(name = "normalized_email", nullable = false, length = 320)
    String normalizedEmail;

    @Column(name = "display_name", nullable = false, length = 200)
    String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    protected UserEntity() {}

    UserEntity(UUID id, OrganizationEntity organization, String username, String normalizedEmail, String displayName) {
        this.id = id;
        this.organization = organization;
        this.username = username;
        this.normalizedEmail = normalizedEmail;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
    }

    UUID getId() {
        return this.id;
    }
}

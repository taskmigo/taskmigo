package io.taskmigo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
@SuppressWarnings("NotNullFieldNotInitialized")
class UserEntity {

    @Id
    UUID id;

    @Column(name = "organization_id", nullable = false)
    UUID organizationId;

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

    UserEntity(UUID id, UUID organizationId, String username, String normalizedEmail, String displayName) {
        this.id = id;
        this.organizationId = organizationId;
        this.username = username;
        this.normalizedEmail = normalizedEmail;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
    }
}

enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
}

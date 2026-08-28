package io.taskmigo.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
@SuppressWarnings({ "ClassNameDiffersFromFileName", "NotNullFieldNotInitialized" })
class UserEntity {

    @Id
    UUID id;

    @Nullable
    @Column(name = "organization_id")
    UUID organizationId;

    @Column(nullable = false, length = 100)
    String username;

    @Nullable
    @Column(name = "normalized_email", length = 320)
    String normalizedEmail;

    @Column(name = "display_name", nullable = false, length = 200)
    String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    @Column(name = "is_system", nullable = false)
    boolean system;

    @Nullable
    @Column(name = "password_hash")
    String passwordHash;

    protected UserEntity() {}

    UserEntity(UUID id, UUID organizationId, String username, String normalizedEmail, String displayName) {
        this.id = id;
        this.organizationId = organizationId;
        this.username = username;
        this.normalizedEmail = normalizedEmail;
        this.displayName = displayName;
        this.status = UserStatus.ACTIVE;
        this.system = false;
        this.passwordHash = null;
    }

    static UserEntity system(String passwordHash) {
        var user = new UserEntity();
        user.id = SystemUser.ID;
        user.organizationId = null;
        user.username = SystemUser.USERNAME;
        user.normalizedEmail = null;
        user.displayName = SystemUser.DISPLAY_NAME;
        user.status = UserStatus.ACTIVE;
        user.system = true;
        user.passwordHash = passwordHash;
        return user;
    }
}

@SuppressWarnings("ClassNameDiffersFromFileName")
enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
}

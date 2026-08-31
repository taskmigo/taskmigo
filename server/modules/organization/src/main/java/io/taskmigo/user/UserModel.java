package io.taskmigo.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
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

    @Column(name = "first_name", nullable = false, length = 100)
    String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    String lastName;

    @ElementCollection
    @CollectionTable(name = "user_emails", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "normalized_email", nullable = false, length = 320)
    @SuppressWarnings("CanBeFinal")
    Set<String> emails = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    @Nullable
    @Column(name = "password_hash")
    String passwordHash;

    protected UserEntity() {}

    UserEntity(
        UUID id,
        @Nullable UUID organizationId,
        String username,
        Set<String> emails,
        String firstName,
        String lastName
    ) {
        this.id = id;
        this.organizationId = organizationId;
        this.username = username;
        this.emails.addAll(emails);
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = UserStatus.ACTIVE;
        this.passwordHash = null;
    }

    String displayName() {
        return (this.firstName + " " + this.lastName).trim();
    }
}

@SuppressWarnings("ClassNameDiffersFromFileName")
enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
}

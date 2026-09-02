package io.taskmigo.auth;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role_id", nullable = false)
    @SuppressWarnings("CanBeFinal")
    Set<UUID> roleIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_statements", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "statement_id", nullable = false)
    @SuppressWarnings("CanBeFinal")
    Set<UUID> statementIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    @Nullable
    @Column(name = "password_hash")
    String passwordHash;

    protected UserEntity() {}

    UserEntity(UUID id, String username, Set<String> emails, Set<UUID> roleIds, String firstName, String lastName) {
        this.id = id;
        this.username = username;
        this.emails.addAll(emails);
        this.roleIds.addAll(roleIds);
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

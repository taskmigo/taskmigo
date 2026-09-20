package io.taskmigo.identity.user.infrastructure.persistence;

import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserStatus;
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
@SuppressWarnings("NotNullFieldNotInitialized")
public class UserEntity {

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
    Set<String> emails = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    @Nullable
    @Column(name = "password_hash")
    String passwordHash;

    protected UserEntity() {}

    private UserEntity(
        UUID id,
        String username,
        Set<String> emails,
        String firstName,
        String lastName,
        UserStatus status,
        @Nullable String passwordHash
    ) {
        this.id = id;
        this.username = username;
        this.emails.addAll(emails);
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.passwordHash = passwordHash;
    }

    static UserEntity from(User user) {
        return new UserEntity(
            user.id(),
            user.username().value(),
            user.profile().emails(),
            user.profile().firstName(),
            user.profile().lastName(),
            user.status(),
            user.credential().passwordHash()
        );
    }

    User toDomain() {
        return User.restore(
            this.id,
            this.username,
            Set.copyOf(this.emails),
            this.firstName,
            this.lastName,
            this.status,
            this.passwordHash
        );
    }

    UUID id() {
        return this.id;
    }

    String username() {
        return this.username;
    }

    String firstName() {
        return this.firstName;
    }

    String lastName() {
        return this.lastName;
    }

    Set<String> emails() {
        return Set.copyOf(this.emails);
    }

    UserStatus status() {
        return this.status;
    }

    @Nullable
    String passwordHash() {
        return this.passwordHash;
    }
}

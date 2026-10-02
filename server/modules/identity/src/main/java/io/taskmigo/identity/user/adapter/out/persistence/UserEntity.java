package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
@SuppressWarnings("NotNullFieldNotInitialized")
public class UserEntity {

    @Id
    UUID id;

    @Nullable
    @Column(length = 100)
    String username;

    @Nullable
    @Column(name = "first_name", length = 100)
    String firstName;

    @Nullable
    @Column(name = "last_name", length = 100)
    String lastName;

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "user_emails", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "normalized_email", nullable = false, length = 320)
    Set<String> emails = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    @Nullable
    @Column(name = "retained_at")
    Instant retainedAt;

    @Nullable
    @Column(name = "password_hash")
    String passwordHash;

    protected UserEntity() {}

    private UserEntity(
        UUID id,
        @Nullable String username,
        Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        UserStatus status,
        @Nullable Instant retainedAt,
        @Nullable String passwordHash
    ) {
        this.id = id;
        this.username = username;
        this.emails.addAll(emails);
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
        this.retainedAt = retainedAt;
        this.passwordHash = passwordHash;
    }

    static UserEntity from(User user) {
        return new UserEntity(
            user.id(),
            user.status() == UserStatus.PURGED ? null : user.username().value(),
            user.status() == UserStatus.PURGED ? Set.of() : user.profile().emails(),
            user.status() == UserStatus.PURGED ? null : user.profile().firstName(),
            user.status() == UserStatus.PURGED ? null : user.profile().lastName(),
            user.status(),
            user.retainedAt(),
            user.credential().passwordHash()
        );
    }

    User toDomain() {
        if (this.status == UserStatus.PURGED) {
            return User.restorePurged(this.id, java.util.Objects.requireNonNull(this.retainedAt));
        }
        return User.restore(
            this.id,
            this.username,
            Set.copyOf(this.emails),
            this.firstName,
            this.lastName,
            this.status,
            this.retainedAt,
            this.passwordHash
        );
    }

    UUID id() {
        return this.id;
    }

    @Nullable
    String username() {
        return this.username;
    }

    @Nullable
    String firstName() {
        return this.firstName;
    }

    @Nullable
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
    Instant retainedAt() {
        return this.retainedAt;
    }

    @Nullable
    String passwordHash() {
        return this.passwordHash;
    }
}

package io.taskmigo.identity.persistence.user;

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
    @SuppressWarnings("CanBeFinal")
    Set<String> emails = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    UserStatus status;

    @Nullable
    @Column(name = "password_hash")
    String passwordHash;

    protected UserEntity() {}

    public UserEntity(UUID id, String username, Set<String> emails, String firstName, String lastName) {
        this.id = id;
        this.username = username;
        this.emails.addAll(emails);
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = UserStatus.ACTIVE;
        this.passwordHash = null;
    }

    public String username() {
        return this.username;
    }

    public String firstName() {
        return this.firstName;
    }

    public String lastName() {
        return this.lastName;
    }

    public Set<String> emails() {
        return Set.copyOf(this.emails);
    }

    public UserStatus status() {
        return this.status;
    }

    public @Nullable String passwordHash() {
        return this.passwordHash;
    }

    public String displayName() {
        return (this.firstName + " " + this.lastName).trim();
    }

    public UUID id() {
        return this.id;
    }

    public void replaceEmails(Set<String> emails) {
        this.emails.clear();
        this.emails.addAll(emails);
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }
}

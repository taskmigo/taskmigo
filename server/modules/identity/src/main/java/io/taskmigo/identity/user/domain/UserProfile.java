package io.taskmigo.identity.user.domain;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/// Represents normalized User profile and email state.
public record UserProfile(Set<String> emails, String firstName, String lastName) {
    public UserProfile {
        emails = normalizeEmails(emails);
        firstName = required(firstName, "firstName");
        lastName = required(lastName, "lastName");
    }

    /// Creates normalized profile state from application input.
    public static UserProfile of(
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName
    ) {
        return new UserProfile(
            normalizeEmails(emails),
            required(firstName, "firstName"),
            required(lastName, "lastName")
        );
    }

    /// Returns the stable display name derived from normalized name state.
    public String displayName() {
        return (this.firstName + " " + this.lastName).trim();
    }

    private static Set<String> normalizeEmails(@Nullable Collection<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return Set.of();
        }
        return emails
            .stream()
            .map(email -> required(email, "email").toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw UserRuleViolation.required(field);
        }
        return value.trim();
    }
}

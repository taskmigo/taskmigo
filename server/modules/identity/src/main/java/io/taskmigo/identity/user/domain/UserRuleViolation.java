package io.taskmigo.identity.user.domain;

import java.io.Serial;

/// Reports a violated User invariant before an application boundary translates it to a stable public failure.
public final class UserRuleViolation extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Reason {
        REQUIRED_FIELD,
        RESERVED_SYSTEM_USERNAME,
        SYSTEM_INITIAL_PASSWORD_REQUIRED,
        SYSTEM_USER_DELETION_FORBIDDEN,
    }

    private final Reason reason;
    private final String detail;

    private UserRuleViolation(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
        this.detail = detail;
    }

    public Reason reason() {
        return this.reason;
    }

    public String detail() {
        return this.detail;
    }

    static UserRuleViolation required(String field) {
        return new UserRuleViolation(Reason.REQUIRED_FIELD, field + " is required");
    }

    static UserRuleViolation reservedSystemUsername() {
        return new UserRuleViolation(Reason.RESERVED_SYSTEM_USERNAME, "Username is reserved for the system user");
    }

    static UserRuleViolation systemInitialPasswordRequired() {
        return new UserRuleViolation(
            Reason.SYSTEM_INITIAL_PASSWORD_REQUIRED,
            "An initial password hash is required for the system user"
        );
    }

    static UserRuleViolation systemUserDeletionForbidden() {
        return new UserRuleViolation(Reason.SYSTEM_USER_DELETION_FORBIDDEN, "The system user cannot be deleted");
    }
}

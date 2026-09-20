package io.taskmigo.authorization.role.domain;

import java.io.Serial;

/// Reports a violated canonical Role invariant before application boundaries translate it to a stable failure.
public final class RoleRuleViolation extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Reason {
        INVALID_CODE,
        REQUIRED_FIELD,
        FIELD_TOO_LONG,
    }

    private final Reason reason;
    private final String detail;

    private RoleRuleViolation(Reason reason, String detail) {
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

    static RoleRuleViolation invalidCode() {
        return new RoleRuleViolation(Reason.INVALID_CODE, "code must match [a-zA-Z0-9_ -]{6,255}");
    }

    static RoleRuleViolation required(String field) {
        return new RoleRuleViolation(Reason.REQUIRED_FIELD, field + " is required");
    }

    static RoleRuleViolation tooLong(String field, int maxLength) {
        return new RoleRuleViolation(Reason.FIELD_TOO_LONG, field + " must not exceed " + maxLength + " characters");
    }
}

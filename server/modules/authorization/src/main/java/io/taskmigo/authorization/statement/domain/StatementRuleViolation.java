package io.taskmigo.authorization.statement.domain;

import java.io.Serial;

/// Reports a violated canonical Statement invariant before application boundaries translate it to a stable failure.
public final class StatementRuleViolation extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Reason {
        INVALID_CODE,
        REQUIRED_FIELD,
        FIELD_TOO_LONG,
        DUPLICATE_CODE,
    }

    private final Reason reason;
    private final String detail;

    private StatementRuleViolation(Reason reason, String detail) {
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

    static StatementRuleViolation invalidCode() {
        return new StatementRuleViolation(
            Reason.INVALID_CODE,
            "code must match [a-zA-Z0-9_-]{6,255}"
        );
    }

    static StatementRuleViolation required(String field) {
        return new StatementRuleViolation(Reason.REQUIRED_FIELD, field + " is required");
    }

    static StatementRuleViolation nonBlank(String field) {
        return new StatementRuleViolation(Reason.REQUIRED_FIELD, field + " must not be blank");
    }

    static StatementRuleViolation tooLong(String field, int maxLength) {
        return new StatementRuleViolation(
            Reason.FIELD_TOO_LONG,
            field + " must not exceed " + maxLength + " characters"
        );
    }

    public static StatementRuleViolation duplicateCode() {
        return new StatementRuleViolation(Reason.DUPLICATE_CODE, "Statement code already exists");
    }
}

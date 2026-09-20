package io.taskmigo.identity.group.domain;

import java.io.Serial;

/// Reports a violated canonical Group invariant before application boundaries translate it to a stable public failure.
public final class GroupRuleViolation extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Reason {
        REQUIRED_FIELD,
    }

    private final Reason reason;
    private final String detail;

    private GroupRuleViolation(Reason reason, String detail) {
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

    static GroupRuleViolation required(String field) {
        return new GroupRuleViolation(Reason.REQUIRED_FIELD, field + " is required");
    }
}

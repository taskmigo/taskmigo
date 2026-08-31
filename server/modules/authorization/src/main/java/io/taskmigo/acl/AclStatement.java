package io.taskmigo.acl;

import io.taskmigo.acl.ResponseAclPolicy.FieldSelection;

/// A reusable authorization statement that can be attached to one or more Roles.
public record AclStatement(
    String key,
    Mode mode,
    ApiTarget target,
    Effect effect,
    AclExpression when,
    FieldSelection fields
) {
    public enum Mode {
        REQUEST,
        RESPONSE,
    }

    public enum Effect {
        ALLOW,
        DENY,
    }
}

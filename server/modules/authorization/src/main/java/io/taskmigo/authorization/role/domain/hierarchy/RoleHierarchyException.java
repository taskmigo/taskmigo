package io.taskmigo.authorization.role.domain.hierarchy;

import java.io.Serial;

/// Reports an invalid Role hierarchy mutation.
public final class RoleHierarchyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoleHierarchyException(String message) {
        super(message);
    }
}

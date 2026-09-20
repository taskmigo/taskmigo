package io.taskmigo.identity.group.domain.hierarchy;

import java.io.Serial;

/// Reports an invalid Group hierarchy mutation.
public final class GroupHierarchyException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GroupHierarchyException(String message) {
        super(message);
    }
}

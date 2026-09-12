package io.taskmigo.authorization.role;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports an invalid authorization role graph.
public final class RoleHierarchyException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// Creates an exception for a role graph that violates the acyclic hierarchy contract.
    public RoleHierarchyException(String message) {
        super(DomainFailureType.BAD_REQUEST, message);
    }
}

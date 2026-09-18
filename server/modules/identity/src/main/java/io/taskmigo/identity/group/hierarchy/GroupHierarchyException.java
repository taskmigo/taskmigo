package io.taskmigo.identity.group.hierarchy;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;
import java.io.Serial;

/// Reports an invalid Identity Group graph.
public final class GroupHierarchyException extends DomainException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// Creates an exception for a Group graph that violates the acyclic hierarchy contract.
    public GroupHierarchyException(String message) {
        super(DomainFailureType.BAD_REQUEST, message);
    }
}

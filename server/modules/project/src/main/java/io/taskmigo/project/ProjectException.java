package io.taskmigo.project;

import io.taskmigo.foundation.DomainException;
import io.taskmigo.foundation.DomainFailureType;

/// Reports a project-domain failure with a transport-neutral category.
public final class ProjectException extends DomainException {

    public enum Type {
        BAD_REQUEST,
        NOT_FOUND,
        CONFLICT,
    }

    ProjectException(Type type, String message) {
        super(DomainFailureType.valueOf(type.name()), message);
    }

    ProjectException(Type type, String message, Throwable cause) {
        super(DomainFailureType.valueOf(type.name()), message, cause);
    }
}

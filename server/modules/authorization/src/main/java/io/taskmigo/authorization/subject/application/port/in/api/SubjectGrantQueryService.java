package io.taskmigo.authorization.subject.application.port.in.api;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Set;
import java.util.UUID;

/// Reads direct Access Control grants for an opaque subject.
public interface SubjectGrantQueryService {
    /// Returns the directly granted Role identifiers.
    Set<UUID> roleIds(SubjectRef subject);

    /// Returns the directly granted Statement identifiers.
    Set<UUID> statementIds(SubjectRef subject);
}

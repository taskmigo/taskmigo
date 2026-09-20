package io.taskmigo.authorization.application.port.out;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Set;
import java.util.UUID;

/// Resolves transitive authorization subjects while Access Control remains independent from subject owners.
public interface EffectiveSubjectResolver {
    /// Returns the principal subject and every transitive subject that contributes authorization grants.
    Set<SubjectRef> resolve(UUID principalId);

    /// Expands one opaque subject to itself and every transitive subject that contributes authorization grants.
    Set<SubjectRef> expand(SubjectRef subject);
}

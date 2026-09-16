package io.taskmigo.authorization.spi;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Set;
import java.util.UUID;

/// Resolves the effective authorization subjects represented by one authenticated principal.
public interface EffectiveSubjectResolver {
    /// Returns the principal subject and every transitive subject that contributes authorization grants.
    Set<SubjectRef> resolve(UUID principalId);
}

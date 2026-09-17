package io.taskmigo.authorization.subject;

import io.taskmigo.authorization.role.RoleInfo;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Defines the application contract for subject-binding use cases.
public interface SubjectGrantService {
    void setRoles(SubjectRef subject, Collection<UUID> roleIds);
    void setStatements(SubjectRef subject, Collection<UUID> statementIds);
    Set<UUID> roleIds(SubjectRef subject);
    Set<UUID> statementIds(SubjectRef subject);
    List<RoleInfo> effectiveRoles(Collection<SubjectRef> subjects);
}

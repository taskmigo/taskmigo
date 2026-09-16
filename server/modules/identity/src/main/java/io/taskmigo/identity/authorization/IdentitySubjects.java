package io.taskmigo.identity.authorization;

import io.taskmigo.authorization.subject.SubjectRef;
import java.util.UUID;

/// Maps Identity-owned resources to opaque Access Control subjects.
public final class IdentitySubjects {

    private static final String USER = "identity:user";
    private static final String GROUP = "identity:group";

    private IdentitySubjects() {}

    /// Returns the Access Control subject for one User.
    public static SubjectRef user(UUID userId) {
        return new SubjectRef(USER, userId);
    }

    /// Returns the Access Control subject for one Group.
    public static SubjectRef group(UUID groupId) {
        return new SubjectRef(GROUP, groupId);
    }
}

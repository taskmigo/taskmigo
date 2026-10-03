package io.taskmigo.identity.user.application.port.in.internal;

import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;

/// Applies configured User deletion semantics to a locked normal User inside the caller-owned transaction.
public interface UserDeletionLifecycleService {
    void delete(User target, UserMutationActor actor, Instant occurredAt);
}

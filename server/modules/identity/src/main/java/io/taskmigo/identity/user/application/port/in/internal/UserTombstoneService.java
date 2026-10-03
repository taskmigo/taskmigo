package io.taskmigo.identity.user.application.port.in.internal;

import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;

/// Applies the atomic tombstone mutation to a locked User inside the caller-owned transaction.
public interface UserTombstoneService {
    void tombstone(User target, UserMutationActor actor, Instant tombstonedAt);
}

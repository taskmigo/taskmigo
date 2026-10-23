package io.taskmigo.identity.user.application;

import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.api.UserSessionLifecycleService;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/// Keeps authenticated-state writes ordered with User deletion using the existing persistent row lock.
@Transactional
public class DefaultUserSessionLifecycleService implements UserSessionLifecycleService {

    private final UserCommandService users;

    public DefaultUserSessionLifecycleService(UserCommandService users) {
        this.users = users;
    }

    @Override
    public boolean runIfActive(UUID userId, Runnable write) {
        var user = this.users.findByIdForUpdate(userId);
        if (user.isEmpty() || user.get().status() != UserStatus.ACTIVE) {
            return false;
        }
        write.run();
        return true;
    }
}

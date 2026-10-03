package io.taskmigo.identity.user.application.service;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import java.util.UUID;

/// Keeps authenticated-state writes ordered with User deletion using the existing persistent row lock.
public final class DefaultUserSessionLifecycleService implements UserSessionLifecycleService {

    private final UserCommandService users;
    private final TransactionRunner transactions;

    public DefaultUserSessionLifecycleService(UserCommandService users, TransactionRunner transactions) {
        this.users = users;
        this.transactions = transactions;
    }

    @Override
    public boolean runIfActive(UUID userId, Runnable write) {
        return this.transactions.write(() -> {
            var user = this.users.findByIdForUpdate(userId);
            if (user.isEmpty() || user.get().status() != UserStatus.ACTIVE) {
                return false;
            }
            write.run();
            return true;
        });
    }
}

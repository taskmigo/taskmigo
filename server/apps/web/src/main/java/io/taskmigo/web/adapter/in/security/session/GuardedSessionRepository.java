package io.taskmigo.web.adapter.in.security.session;

import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/// Prevents stored servlet authentication from surviving or being recreated after User deletion.
public final class GuardedSessionRepository<S extends Session> implements SessionRepository<S>, DisposableBean {

    private final SessionRepository<S> delegate;
    private final UserService users;
    private final UserSessionLifecycleService lifecycle;

    public GuardedSessionRepository(
        SessionRepository<S> delegate,
        UserService users,
        UserSessionLifecycleService lifecycle
    ) {
        this.delegate = delegate;
        this.users = users;
        this.lifecycle = lifecycle;
    }

    @Override
    public S createSession() {
        return this.delegate.createSession();
    }

    @Override
    public void save(S session) {
        if (UserSessionPrincipal.authentication(session) == null) {
            this.delegate.save(session);
            return;
        }
        UserSessionPrincipal principal = UserSessionPrincipal.fromSession(session);
        if (principal == null || !this.lifecycle.runIfActive(principal.userId(), () -> this.delegate.save(session))) {
            this.delegate.deleteById(session.getId());
        }
    }

    @Override
    public @Nullable S findById(String id) {
        S session = this.delegate.findById(id);
        if (session == null || UserSessionPrincipal.authentication(session) == null) {
            return session;
        }
        UserSessionPrincipal principal = UserSessionPrincipal.fromSession(session);
        if (
            principal != null &&
            this.users
                .find(principal.userId())
                .filter(user -> user.status() == UserStatus.ACTIVE)
                .isPresent()
        ) {
            return session;
        }
        this.delegate.deleteById(id);
        return null;
    }

    @Override
    public void deleteById(String id) {
        this.delegate.deleteById(id);
    }

    @Override
    public void destroy() throws Exception {
        if (this.delegate instanceof DisposableBean disposable) {
            disposable.destroy();
        }
    }
}

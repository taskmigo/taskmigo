package io.taskmigo.web.composition.auth;

import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.web.adapter.in.security.session.GuardedSessionRepository;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import java.time.Duration;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.FlushMode;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/// Composes one guarded PostgreSQL session repository for every servlet authentication flow.
@Configuration(proxyBeanMethods = false)
@EnableSpringHttpSession
class HttpSessionConfiguration {

    @Bean
    GuardedSessionRepository<?> sessionRepository(
        JdbcOperations jdbc,
        PlatformTransactionManager manager,
        UserService users,
        UserSessionLifecycleService lifecycle
    ) {
        JdbcIndexedSessionRepository delegate = new JdbcIndexedSessionRepository(
            jdbc,
            new TransactionTemplate(manager)
        );
        delegate.setFlushMode(FlushMode.ON_SAVE);
        delegate.setDefaultMaxInactiveInterval(Duration.ofMinutes(30));
        delegate.setIndexResolver(session -> {
            UserSessionPrincipal principal = UserSessionPrincipal.fromSession(session);
            return principal == null
                ? Map.of()
                : Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal.userId().toString());
        });
        delegate.afterPropertiesSet();
        return new GuardedSessionRepository<>(delegate, users, lifecycle);
    }

    @Bean
    DefaultCookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("JSESSIONID");
        return serializer;
    }
}

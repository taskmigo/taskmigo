package io.taskmigo.web.composition.auth;

import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.web.adapter.in.security.session.UserSessionPrincipal;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.jackson.SecurityJacksonModules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration(proxyBeanMethods = false)
class OAuthPersistenceConfiguration {

    @Bean
    JdbcRegisteredClientRepository jdbcRegisteredClientRepository(JdbcOperations jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
        JdbcOperations jdbc,
        JdbcRegisteredClientRepository clients,
        UserSessionLifecycleService lifecycle
    ) {
        JsonMapper mapper = authorizationMapper();
        JdbcOAuth2AuthorizationService delegate = new JdbcOAuth2AuthorizationService(jdbc, clients);
        delegate.setAuthorizationRowMapper(
            new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationRowMapper(clients, mapper)
        );
        delegate.setAuthorizationParametersMapper(
            new JdbcOAuth2AuthorizationService.JsonMapperOAuth2AuthorizationParametersMapper(mapper)
        );
        return new GuardedOAuth2AuthorizationService(delegate, lifecycle);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
        JdbcOperations jdbc,
        JdbcRegisteredClientRepository clients,
        UserSessionLifecycleService lifecycle
    ) {
        return new GuardedOAuth2AuthorizationConsentService(
            new JdbcOAuth2AuthorizationConsentService(jdbc, clients),
            lifecycle
        );
    }

    static JsonMapper authorizationMapper() {
        var validator = BasicPolymorphicTypeValidator.builder().allowIfSubType(UserSessionPrincipal.class);
        return JsonMapper.builder()
            .addModules(
                SecurityJacksonModules.getModules(
                    Objects.requireNonNull(OAuthPersistenceConfiguration.class.getClassLoader()),
                    validator
                )
            )
            .build();
    }
}

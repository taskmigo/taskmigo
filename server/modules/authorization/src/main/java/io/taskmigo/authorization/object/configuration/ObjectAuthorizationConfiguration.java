package io.taskmigo.authorization.object.configuration;

import io.taskmigo.authorization.object.application.api.AuthorizationGateway;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.object.application.resolution.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.object.application.service.DefaultAuthorizationGateway;
import io.taskmigo.authorization.object.application.service.ObjectAuthorizationService;
import io.taskmigo.authorization.request.application.api.RequestAuthorization;
import io.taskmigo.language.LanguageCompiler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ObjectAuthorizationConfiguration {

    @Bean
    ObjectAuthorization objectAuthorization(
        LanguageCompiler compiler,
        ObjectAuthorizationTargetResolver targetResolver
    ) {
        return new ObjectAuthorizationService(compiler, targetResolver);
    }

    @Bean
    AuthorizationGateway authorizationGateway(
        RequestAuthorization requestAuthorization,
        ObjectAuthorization objectAuthorization
    ) {
        return new DefaultAuthorizationGateway(requestAuthorization, objectAuthorization);
    }
}

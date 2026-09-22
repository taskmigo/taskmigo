package io.taskmigo.authorization.object.composition;

import io.taskmigo.authorization.object.application.port.in.api.ObjectAuthorization;
import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.object.application.service.ObjectAuthorizationService;
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
}

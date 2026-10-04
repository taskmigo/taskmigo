package io.taskmigo.authorization.request.composition;

import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.authorization.request.application.port.in.api.RequestAuthorization;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatementResolver;
import io.taskmigo.authorization.request.application.service.RequestAuthorizationService;
import io.taskmigo.authorization.request.application.service.StatementArtifactFactory;
import io.taskmigo.language.LanguageCompiler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RequestAuthorizationConfiguration {

    @Bean
    StatementArtifactFactory statementArtifactFactory(
        LanguageCompiler compiler,
        ObjectAuthorizationTargetResolver targetResolver
    ) {
        return new StatementArtifactFactory(compiler, targetResolver);
    }

    @Bean
    RequestAuthorization requestAuthorization(
        EffectiveStatementResolver statements,
        StatementArtifactFactory artifacts
    ) {
        return new RequestAuthorizationService(statements, artifacts);
    }
}

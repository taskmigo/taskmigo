package io.taskmigo.authorization.request.composition;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.request.application.port.in.api.RequestAuthorization;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatementResolver;
import io.taskmigo.authorization.request.application.service.RequestAuthorizationService;
import io.taskmigo.authorization.request.application.service.StatementArtifactFactory;
import io.taskmigo.authorization.spi.ObjectAuthorizationTargetResolver;
import io.taskmigo.language.LanguageCompiler;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RequestAuthorizationConfiguration {

    @Bean
    StatementArtifactFactory statementArtifactFactory(
        LanguageCompiler compiler,
        List<ObjectAuthorizationSchema<?>> schemas,
        ObjectAuthorizationTargetResolver targetResolver
    ) {
        return new StatementArtifactFactory(compiler, schemas, targetResolver);
    }

    @Bean
    RequestAuthorization requestAuthorization(
        EffectiveStatementResolver statements,
        StatementArtifactFactory artifacts
    ) {
        return new RequestAuthorizationService(statements, artifacts);
    }
}

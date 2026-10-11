package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.authorization.object.application.port.out.ObjectAuthorizationTargetResolver;
import io.taskmigo.language.LanguageCompiler;
import io.taskmigo.query.QuerySchemaView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers the generic Language services for authorization adapters.
@Configuration
@EnableConfigurationProperties(EmbeddedLanguageCompilerProperties.class)
public class AuthorizationEmbeddedLanguageConfiguration {

    @Bean
    LanguageCompiler languageCompiler(EmbeddedLanguageCompilerProperties properties) {
        return new LanguageCompiler(properties.limits());
    }

    /// Provides an all-schema target resolver when an application has no transport-specific target metadata.
    @Bean
    @ConditionalOnMissingBean(ObjectAuthorizationTargetResolver.class)
    ObjectAuthorizationTargetResolver objectAuthorizationTargetResolver(List<QuerySchemaView> schemas) {
        return ObjectAuthorizationTargetResolver.all(schemas);
    }
}

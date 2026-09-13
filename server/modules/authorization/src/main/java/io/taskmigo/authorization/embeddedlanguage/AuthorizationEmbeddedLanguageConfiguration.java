package io.taskmigo.authorization.embeddedlanguage;

import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.spi.ObjectAuthorizationTargetResolver;
import io.taskmigo.language.LanguageCompiler;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers the generic Language services for authorization adapters.
@Configuration
@EnableConfigurationProperties(EmbeddedLanguageCompilerProperties.class)
public class AuthorizationEmbeddedLanguageConfiguration {

    /// Provides the bounded Language compiler.
    @Bean
    LanguageCompiler languageCompiler(EmbeddedLanguageCompilerProperties properties) {
        return new LanguageCompiler(properties.limits());
    }

    /// Provides an all-schema target resolver when an application has no transport-specific target metadata.
    @Bean
    @ConditionalOnMissingBean(ObjectAuthorizationTargetResolver.class)
    ObjectAuthorizationTargetResolver objectAuthorizationTargetResolver(List<ObjectAuthorizationSchema<?>> schemas) {
        return ObjectAuthorizationTargetResolver.all(schemas);
    }
}

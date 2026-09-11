package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationSchemaRegistry;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageCompiler;
import io.taskmigo.embeddedlanguage.EmbeddedLanguageEvaluator;
import io.taskmigo.embeddedlanguage.EmbeddedLanguagePartialEvaluator;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers the generic Embedded Language services for authorization adapters.
@Configuration
@EnableConfigurationProperties(EmbeddedLanguageCompilerProperties.class)
public class AuthorizationEmbeddedLanguageConfiguration {

    /// Provides the bounded Embedded Language compiler.
    @Bean
    EmbeddedLanguageCompiler embeddedLanguageCompiler(EmbeddedLanguageCompilerProperties properties) {
        return new EmbeddedLanguageCompiler(properties.limits());
    }

    /// Provides strict Embedded Language evaluation.
    @Bean
    EmbeddedLanguageEvaluator embeddedLanguageEvaluator() {
        return new EmbeddedLanguageEvaluator();
    }

    /// Provides generic partial evaluation before authorization query lowering.
    @Bean
    EmbeddedLanguagePartialEvaluator embeddedLanguagePartialEvaluator() {
        return new EmbeddedLanguagePartialEvaluator();
    }

    /// Provides an explicit all-schema registry when an application has not registered route mappings.
    @Bean
    @ConditionalOnMissingBean(ObjectAuthorizationSchemaRegistry.class)
    ObjectAuthorizationSchemaRegistry objectAuthorizationSchemaRegistry(List<ObjectAuthorizationSchema<?>> schemas) {
        return ObjectAuthorizationSchemaRegistry.all(schemas);
    }
}

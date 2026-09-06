package io.taskmigo.auth.authorization.policy;

import io.taskmigo.policy.PolicyCompiler;
import io.taskmigo.policy.PolicyEvaluator;
import io.taskmigo.policy.PolicyPartialEvaluator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Registers the generic Policy Language services for authorization adapters.
@Configuration
@EnableConfigurationProperties(PolicyCompilerProperties.class)
public class AuthorizationPolicyConfiguration {

    /// Provides the bounded Policy Language compiler.
    @Bean
    PolicyCompiler policyCompiler(PolicyCompilerProperties properties) {
        return new PolicyCompiler(properties.limits());
    }

    /// Provides strict Policy Language evaluation.
    @Bean
    PolicyEvaluator policyEvaluator() {
        return new PolicyEvaluator();
    }

    /// Provides generic partial evaluation before authorization query lowering.
    @Bean
    PolicyPartialEvaluator policyPartialEvaluator() {
        return new PolicyPartialEvaluator();
    }
}

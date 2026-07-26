package com.taskmigo.console.access.internal.configuration.oauth;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.taskmigo.console.access.internal.application.oauth.client.OAuthClientSynchronizer;
import com.taskmigo.console.access.internal.application.signing.SigningKeyLifecycle;
import java.time.Clock;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

@Configuration
public class AuthorizationServerConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  OAuth2AuthorizationService authorizationService(
      JdbcOperations jdbcOperations, RegisteredClientRepository clients) {
    return new JdbcOAuth2AuthorizationService(jdbcOperations, clients);
  }

  @Bean
  OAuth2AuthorizationConsentService authorizationConsentService(
      JdbcOperations jdbcOperations, RegisteredClientRepository clients) {
    return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, clients);
  }

  @Bean
  JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
    return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
  }

  @Bean
  ApplicationRunner authorizationBootstrap(
      OAuthClientSynchronizer clients,
      OAuth2AuthorizationServerProperties properties,
      SigningKeyLifecycle signingKeys) {
    return arguments -> {
      clients.synchronize(properties);
      signingKeys.ensureActiveKey();
    };
  }
}

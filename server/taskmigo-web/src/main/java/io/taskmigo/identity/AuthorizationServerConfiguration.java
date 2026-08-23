package io.taskmigo.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ IdentityProperties.class, OAuth2AuthorizationServerProperties.class })
@EnableScheduling
class AuthorizationServerConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    RSAKey signingKey(IdentityProperties properties) {
        return SigningKeyStore.load(
            properties.signingKeyFile(),
            properties.signingKeyId(),
            properties.signingKeyAutoCreate()
        );
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(RSAKey signingKey) {
        return new ImmutableJWKSet<>(new JWKSet(signingKey));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> servicePrincipalClaims() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) return;

            Set<String> permissions = InternalClientMetadata.permissions(context.getRegisteredClient());
            context.getClaims().subject(context.getRegisteredClient().getClientId());
            context.getClaims().claim("principal_type", "service");
            context.getClaims().claim("permissions", List.copyOf(permissions));
        };
    }
}

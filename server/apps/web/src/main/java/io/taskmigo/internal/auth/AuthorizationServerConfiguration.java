package io.taskmigo.internal.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.taskmigo.auth.user.SystemUser;
import io.taskmigo.auth.user.UserService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/// Configures the OAuth authorization-server primitives and the claims used to identify Taskmigo principals.
///
/// Persisted interactive accounts share one user model. Authorization decisions are delegated to the API ACL layer.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ IdentityProperties.class, OAuth2AuthorizationServerProperties.class })
class AuthorizationServerConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService interactiveUsers(UserService userService) {
        return username -> {
            var user = userService
                .findForAuthentication(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            String passwordHash = user.passwordHash();
            if (passwordHash == null) throw new UsernameNotFoundException("User has no interactive credential");

            return User.withUsername(user.username())
                .password(passwordHash)
                .roles(SystemUser.USERNAME.equals(user.username()) ? "SYSTEM" : "USER")
                .disabled(!user.active())
                .build();
        };
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
    OAuth2TokenCustomizer<JwtEncodingContext> principalClaims(UserService userService) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;

            if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                context.getClaims().subject(context.getRegisteredClient().getClientId());
                context.getClaims().claim("principal_type", "service");
                userService.findForAuthentication(SystemUser.USERNAME).ifPresent(user -> {
                    context.getClaims().claim("user_id", user.id().toString());
                    context.getClaims().claim("principal_username", user.username());
                });
                return;
            }

            var authorization = context.getAuthorization();
            if (authorization == null) return;
            userService.findForAuthentication(authorization.getPrincipalName()).ifPresent(user -> {
                context.getClaims().claim("principal_type", "user");
                context.getClaims().claim("user_id", user.id().toString());
                context.getClaims().claim("principal_username", user.username());
            });
        };
    }
}

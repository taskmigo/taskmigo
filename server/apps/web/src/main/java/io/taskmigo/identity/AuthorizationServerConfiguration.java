package io.taskmigo.identity;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.taskmigo.user.UserService;
import java.util.ArrayList;
import java.util.Set;
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

/// Configures the OAuth authorization-server primitives and the claims used to identify Taskmigo service principals.
///
/// Access tokens issued through `client_credentials` use the registered client id as their subject and include
/// `principal_type=service` plus the permissions assigned to managed internal clients. Other token flows keep Spring
/// Authorization Server's default claims.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    IdentityProperties.class,
    BrowserAuthenticationProperties.class,
    OAuth2AuthorizationServerProperties.class,
})
class AuthorizationServerConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService interactiveUsers(
        BrowserAuthenticationProperties properties,
        PasswordEncoder passwordEncoder,
        UserService userService
    ) {
        var developmentUser = properties.developmentUser();
        if (!developmentUser.enabled()) {
            return username -> {
                throw new UsernameNotFoundException("Interactive login is disabled");
            };
        }
        if (developmentUser.password().isBlank()) {
            throw new IllegalStateException(
                "Development login password must not be blank when the development user is enabled"
            );
        }

        String encodedPassword = passwordEncoder.encode(developmentUser.password());
        return username -> {
            if (!developmentUser.username().equals(username)) {
                throw new UsernameNotFoundException("User not found");
            }

            var user = userService
                .findForAuthentication(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            return User.withUsername(user.username())
                .password(encodedPassword)
                .roles("USER")
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
    OAuth2TokenCustomizer<JwtEncodingContext> servicePrincipalClaims() {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) return;

            Set<String> permissions = InternalClientMetadata.permissions(context.getRegisteredClient());
            context.getClaims().subject(context.getRegisteredClient().getClientId());
            context.getClaims().claim("principal_type", "service");
            context.getClaims().claim("permissions", new ArrayList<>(permissions));
        };
    }
}

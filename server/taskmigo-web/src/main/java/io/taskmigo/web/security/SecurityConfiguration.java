package io.taskmigo.web.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize ->
            authorize
                .requestMatchers("/api/v0/**")
                .hasAllAuthorities(
                    "SCOPE_taskmigo.api",
                    "PERMISSION_" + ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE
                )
                .anyRequest()
                .permitAll()
        )
            .csrf(csrf -> csrf.ignoringRequestMatchers("/api/v0/**"))
            .oauth2AuthorizationServer(Customizer.withDefaults())
            .oauth2ResourceServer(resourceServer ->
                resourceServer.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    RSAKey signingKey(SecurityProperties properties) {
        return SigningKeyStore.loadOrCreate(properties.signingKeyFile(), properties.signingKeyId());
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
    OAuth2TokenCustomizer<JwtEncodingContext> servicePrincipalClaims(ServicePrincipalAuthorization servicePrincipals) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) return;
            if (!AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) return;

            Set<String> permissions = servicePrincipals.permissions(context.getRegisteredClient().getId());
            context.getClaims().subject(context.getRegisteredClient().getClientId());
            context.getClaims().claim("principal_type", "service");
            context.getClaims().claim("permissions", List.copyOf(permissions));
        };
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(SecurityProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtGrantedAuthoritiesConverter permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName("permissions");
        permissions.setAuthorityPrefix("PERMISSION_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(Objects.requireNonNull(scopes.convert(jwt)));
            authorities.addAll(Objects.requireNonNull(permissions.convert(jwt)));
            return authorities;
        });
        return converter;
    }
}

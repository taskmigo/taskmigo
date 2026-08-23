package io.taskmigo.web.security;

import io.taskmigo.identity.ServicePrincipalPermissions;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ApiSecurityConfiguration {

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

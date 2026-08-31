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
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ApiSecurityConfiguration {

    private static final String VERSIONED_API_PATTERN = "/api/v*/**";

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        VersionedApiSecurityErrorHandler securityErrors,
        AuthorizationServerSettings authorizationServerSettings,
        ApiAclSupport acl
    ) {
        RequestMatcher authEndpointMatcher = request ->
            authorizationServerSettings.getAuthorizationEndpoint().equals(request.getServletPath());

        http.authorizeHttpRequests(authorize ->
            authorize
                .requestMatchers(authEndpointMatcher)
                .authenticated()
                .requestMatchers(VERSIONED_API_PATTERN)
                .hasAllAuthorities(
                    "SCOPE_taskmigo.api",
                    "PERMISSION_" + ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE
                )
                .anyRequest()
                .permitAll()
        )
            .csrf(csrf -> csrf.ignoringRequestMatchers(VERSIONED_API_PATTERN))
            .exceptionHandling(exceptions ->
                exceptions.defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    authEndpointMatcher
                )
            )
            .formLogin(Customizer.withDefaults())
            .oauth2AuthorizationServer(authorizationServer -> authorizationServer.oidc(Customizer.withDefaults()))
            .oauth2ResourceServer(resourceServer ->
                resourceServer
                    .authenticationEntryPoint(securityErrors)
                    .accessDeniedHandler(securityErrors)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .addFilterAfter(new ApiAclRequestFilter(acl), BearerTokenAuthenticationFilter.class);
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

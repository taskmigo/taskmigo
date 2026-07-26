package com.taskmigo.console.access.internal.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/**
 * Defines the complete HTTP security routing table. Spring Security evaluates these chains in
 * ascending order and uses the first matching chain.
 *
 * <table>
 * <caption>Security filter chain precedence</caption>
 * <tr><th>Order</th><th>Matcher</th><th>Responsibility</th></tr>
 * <tr><td>100</td><td>Authorization Server endpoints</td><td>OAuth 2.1 and OIDC</td></tr>
 * <tr><td>200</td><td>{@code /api/**}</td><td>JWT Resource Server</td></tr>
 * <tr><td>300</td><td>Fallback</td><td>Form login and browser routes</td></tr>
 * </table>
 */
@Configuration
public class AccessSecurityConfiguration {

  static final int AUTHORIZATION_SERVER_CHAIN_ORDER = 100;
  static final int API_RESOURCE_SERVER_CHAIN_ORDER = 200;
  static final int FORM_LOGIN_CHAIN_ORDER = 300;

  @Bean
  @Order(AUTHORIZATION_SERVER_CHAIN_ORDER)
  SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
    var authorizationServer = new OAuth2AuthorizationServerConfigurer();
    http.securityMatcher(authorizationServer.getEndpointsMatcher())
        .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint("/login")));
    return http.build();
  }

  @Bean
  @Order(API_RESOURCE_SERVER_CHAIN_ORDER)
  SecurityFilterChain apiResourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/api/**")
        .authorizeHttpRequests(
            authorize ->
                authorize.requestMatchers("/api/public").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
    return http.build();
  }

  @Bean
  @Order(FORM_LOGIN_CHAIN_ORDER)
  SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .formLogin(Customizer.withDefaults());
    return http.build();
  }
}

package com.taskmigo.console.access.internal.application.oauth.client;

import java.time.Duration;
import java.util.Set;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;

class SpringRegisteredClientMapperTest {
  private final SpringRegisteredClientMapper mapper = new SpringRegisteredClientMapper();

  SpringRegisteredClientMapperTest() {}

  @Test
  void mapsCompleteConfidentialClientConfiguration() {
    OAuth2AuthorizationServerProperties.Client configured =
        SpringRegisteredClientMapperTest.client("browser", "{noop}secret");
    configured.setJwkSetUri("https://client.example/jwks");
    configured.setTokenEndpointAuthenticationSigningAlgorithm("HS256");
    configured.getToken().setAccessTokenTimeToLive(Duration.ofMinutes(12L));
    configured.getToken().setRefreshTokenTimeToLive(Duration.ofDays(2L));
    configured.getToken().setAccessTokenFormat("reference");
    RegisteredClient mapped = this.mapper.map("browser-registration", configured, null);
    Assertions.assertThat((String) mapped.getId()).isEqualTo("browser-registration");
    Assertions.assertThat((String) mapped.getClientId()).isEqualTo("browser");
    Assertions.assertThat((String) mapped.getClientName()).isEqualTo("Browser client");
    Assertions.assertThat((String) mapped.getClientSecret()).isEqualTo("{noop}secret");
    Assertions.assertThat(mapped.getClientAuthenticationMethods())
        .containsExactly(
            new ClientAuthenticationMethod[] {ClientAuthenticationMethod.CLIENT_SECRET_BASIC});
    Assertions.assertThat(mapped.getAuthorizationGrantTypes())
        .containsExactlyInAnyOrder(
            new AuthorizationGrantType[] {
              AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN
            });
    Assertions.assertThat(mapped.getRedirectUris())
        .containsExactly(new String[] {"https://client.example/callback"});
    Assertions.assertThat(mapped.getPostLogoutRedirectUris())
        .containsExactly(new String[] {"https://client.example/"});
    Assertions.assertThat(mapped.getScopes())
        .containsExactlyInAnyOrder(new String[] {"openid", "api.read"});
    Assertions.assertThat((String) mapped.getClientSettings().getJwkSetUrl())
        .isEqualTo("https://client.example/jwks");
    Assertions.assertThat(
            (String)
                mapped
                    .getClientSettings()
                    .getTokenEndpointAuthenticationSigningAlgorithm()
                    .getName())
        .isEqualTo("HS256");
    Assertions.assertThat((Duration) mapped.getTokenSettings().getAccessTokenTimeToLive())
        .isEqualTo(Duration.ofMinutes(12L));
    Assertions.assertThat((Duration) mapped.getTokenSettings().getRefreshTokenTimeToLive())
        .isEqualTo(Duration.ofDays(2L));
    Assertions.assertThat(mapped.getTokenSettings().getAccessTokenFormat())
        .isEqualTo(OAuth2TokenFormat.REFERENCE);
  }

  @Test
  void updatesExistingClientWithoutChangingDatabaseIdAndClearsOldCollections() {
    RegisteredClient existing =
        RegisteredClient.withId("database-id")
            .clientId("browser")
            .clientSecret("old")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("https://old.example/callback")
            .scope("old.scope")
            .build();
    RegisteredClient mapped =
        this.mapper.map(
            "ignored-registration-id",
            SpringRegisteredClientMapperTest.client("browser", "{noop}new-secret"),
            existing);
    Assertions.assertThat((String) mapped.getId()).isEqualTo("database-id");
    Assertions.assertThat((String) mapped.getClientSecret()).isEqualTo("{noop}new-secret");
    Assertions.assertThat(mapped.getClientAuthenticationMethods())
        .containsExactly(
            new ClientAuthenticationMethod[] {ClientAuthenticationMethod.CLIENT_SECRET_BASIC});
    Assertions.assertThat(mapped.getAuthorizationGrantTypes())
        .doesNotContain(new AuthorizationGrantType[] {AuthorizationGrantType.CLIENT_CREDENTIALS});
    Assertions.assertThat(mapped.getScopes()).doesNotContain(new String[] {"old.scope"});
    Assertions.assertThat(mapped.getRedirectUris())
        .doesNotContain(new String[] {"https://old.example/callback"});
  }

  @Test
  void mapsPublicClientWithoutSecret() {
    OAuth2AuthorizationServerProperties.Client configured =
        SpringRegisteredClientMapperTest.client("public-client", null);
    configured.getRegistration().setClientAuthenticationMethods(Set.of("none"));
    RegisteredClient mapped = this.mapper.map("public", configured, null);
    Assertions.assertThat((String) mapped.getClientSecret()).isNull();
    Assertions.assertThat(mapped.getClientAuthenticationMethods())
        .containsExactly(new ClientAuthenticationMethod[] {ClientAuthenticationMethod.NONE});
  }

  @Test
  void rejectsUnknownAuthenticationSigningAlgorithm() {
    OAuth2AuthorizationServerProperties.Client configured =
        SpringRegisteredClientMapperTest.client("browser", "{noop}secret");
    configured.setTokenEndpointAuthenticationSigningAlgorithm("unknown");
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> this.mapper.map("browser", configured, null))
                .isInstanceOf(IllegalArgumentException.class))
        .hasMessageContaining("UNKNOWN");
  }

  @Test
  void rejectsUnknownIdTokenSigningAlgorithm() {
    OAuth2AuthorizationServerProperties.Client configured =
        SpringRegisteredClientMapperTest.client("browser", "{noop}secret");
    configured.getToken().setIdTokenSignatureAlgorithm("unknown");
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> this.mapper.map("browser", configured, null))
                .isInstanceOf(IllegalArgumentException.class))
        .hasMessageContaining("unknown");
  }

  static OAuth2AuthorizationServerProperties.Client client(String clientId, String secret) {
    OAuth2AuthorizationServerProperties.Client client =
        new OAuth2AuthorizationServerProperties.Client();
    client.getRegistration().setClientId(clientId);
    client.getRegistration().setClientName("Browser client");
    client.getRegistration().setClientSecret(secret);
    client.getRegistration().setClientAuthenticationMethods(Set.of("client_secret_basic"));
    client
        .getRegistration()
        .setAuthorizationGrantTypes(Set.of("authorization_code", "refresh_token"));
    client.getRegistration().setRedirectUris(Set.of("https://client.example/callback"));
    client.getRegistration().setPostLogoutRedirectUris(Set.of("https://client.example/"));
    client.getRegistration().setScopes(Set.of("openid", "api.read"));
    return client;
  }
}

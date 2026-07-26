package com.taskmigo.console.access.internal.application.oauth.client;

import java.util.Locale;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
public class SpringRegisteredClientMapper {
  public RegisteredClient map(
      String registrationId,
      OAuth2AuthorizationServerProperties.Client client,
      RegisteredClient existing) {
    JwsAlgorithm authenticationSigningAlgorithm;
    OAuth2AuthorizationServerProperties.Registration registration = client.getRegistration();
    Assert.hasText(registration.getClientId(), "Client id must not be empty");
    RegisteredClient.Builder builder =
        existing == null
            ? RegisteredClient.withId(registrationId).clientId(registration.getClientId())
            : RegisteredClient.from(existing);
    ClientSettings.Builder clientSettings =
        ClientSettings.builder()
            .requireProofKey(client.isRequireProofKey())
            .requireAuthorizationConsent(client.isRequireAuthorizationConsent());
    if (StringUtils.hasText(client.getJwkSetUri())) {
      clientSettings.jwkSetUrl(client.getJwkSetUri());
    }
    if ((authenticationSigningAlgorithm =
            SpringRegisteredClientMapper.resolveJwsAlgorithm(
                client.getTokenEndpointAuthenticationSigningAlgorithm()))
        != null) {
      clientSettings.tokenEndpointAuthenticationSigningAlgorithm(authenticationSigningAlgorithm);
    }
    builder
        .clientName(
            StringUtils.hasText(registration.getClientName())
                ? registration.getClientName()
                : registrationId)
        .clientSecret(registration.getClientSecret())
        .clientAuthenticationMethods(
            methods -> {
              methods.clear();
              registration.getClientAuthenticationMethods().stream()
                  .map(ClientAuthenticationMethod::new)
                  .forEach(methods::add);
            })
        .authorizationGrantTypes(
            grantTypes -> {
              grantTypes.clear();
              registration.getAuthorizationGrantTypes().stream()
                  .map(AuthorizationGrantType::new)
                  .forEach(grantTypes::add);
            })
        .redirectUris(
            uris -> {
              uris.clear();
              uris.addAll(registration.getRedirectUris());
            })
        .postLogoutRedirectUris(
            uris -> {
              uris.clear();
              uris.addAll(registration.getPostLogoutRedirectUris());
            })
        .scopes(
            scopes -> {
              scopes.clear();
              scopes.addAll(registration.getScopes());
            })
        .clientSettings(clientSettings.build())
        .tokenSettings(SpringRegisteredClientMapper.tokenSettings(client));
    return builder.build();
  }

  private static TokenSettings tokenSettings(OAuth2AuthorizationServerProperties.Client client) {
    OAuth2AuthorizationServerProperties.Token token = client.getToken();
    SignatureAlgorithm idTokenAlgorithm =
        SignatureAlgorithm.from(token.getIdTokenSignatureAlgorithm().toUpperCase(Locale.ROOT));
    Assert.notNull(
        idTokenAlgorithm,
        "Signature algorithm " + token.getIdTokenSignatureAlgorithm() + " is unknown");
    return TokenSettings.builder()
        .authorizationCodeTimeToLive(token.getAuthorizationCodeTimeToLive())
        .accessTokenTimeToLive(token.getAccessTokenTimeToLive())
        .accessTokenFormat(new OAuth2TokenFormat(token.getAccessTokenFormat()))
        .deviceCodeTimeToLive(token.getDeviceCodeTimeToLive())
        .reuseRefreshTokens(token.isReuseRefreshTokens())
        .refreshTokenTimeToLive(token.getRefreshTokenTimeToLive())
        .idTokenSignatureAlgorithm(idTokenAlgorithm)
        .build();
  }

  private static JwsAlgorithm resolveJwsAlgorithm(String algorithm) {
    if (!StringUtils.hasText(algorithm)) {
      return null;
    }
    String name = algorithm.toUpperCase(Locale.ROOT);
    JwsAlgorithm resolved = SignatureAlgorithm.from(name);
    if (resolved == null) {
      resolved = MacAlgorithm.from(name);
    }
    Assert.notNull(resolved, "JWS algorithm " + name + " is unknown");
    return resolved;
  }
}

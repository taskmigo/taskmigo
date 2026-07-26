package com.taskmigo.console;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.taskmigo.console.access.internal.application.oauth.client.OAuthClientSynchronizer;
import com.taskmigo.console.access.internal.application.oauth.management.DeletionConfirmation;
import com.taskmigo.console.access.internal.application.oauth.management.InvalidDeletionConfirmationException;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientManagementService;
import com.taskmigo.console.access.internal.domain.identity.AppUser;
import com.taskmigo.console.access.internal.domain.signing.SigningKey;
import com.taskmigo.console.access.internal.persistence.identity.AppUserRepository;
import com.taskmigo.console.access.internal.persistence.oauth.client.ActiveRegisteredClientRepository;
import com.taskmigo.console.access.internal.persistence.signing.SigningKeyRepository;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "spring.security.oauth2.authorizationserver.issuer=http://localhost:9000",
      "app.security.bootstrap.username=developer",
      "app.security.bootstrap.password=test-password",
      "spring.security.oauth2.authorizationserver.client.browser.registration.client-id=environment-client",
      "spring.security.oauth2.authorizationserver.client.browser.registration.client-secret={noop}environment-secret",
      "spring.security.oauth2.authorizationserver.client.browser.registration.client-authentication-methods=client_secret_basic",
      "spring.security.oauth2.authorizationserver.client.browser.registration.authorization-grant-types=authorization_code,refresh_token",
      "spring.security.oauth2.authorizationserver.client.browser.registration.redirect-uris=http://127.0.0.1:8080/callback",
      "spring.security.oauth2.authorizationserver.client.browser.registration.post-logout-redirect-uris=http://127.0.0.1:8080/",
      "spring.security.oauth2.authorizationserver.client.browser.registration.scopes=openid,profile,email,api.read,api.admin,offline_access",
      "spring.security.oauth2.authorizationserver.client.browser.require-proof-key=true",
      "spring.security.oauth2.authorizationserver.client.browser.require-authorization-consent=false",
      "spring.security.oauth2.authorizationserver.client.service.registration.client-id=service-client",
      "spring.security.oauth2.authorizationserver.client.service.registration.client-secret={noop}service-secret",
      "spring.security.oauth2.authorizationserver.client.service.registration.client-authentication-methods=client_secret_basic",
      "spring.security.oauth2.authorizationserver.client.service.registration.authorization-grant-types=client_credentials",
      "spring.security.oauth2.authorizationserver.client.service.registration.scopes=api.read",
      "spring.security.oauth2.authorizationserver.client.service.require-proof-key=false"
    })
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@TestMethodOrder(value = MethodOrderer.OrderAnnotation.class)
class PersistenceIntegrationTest {
  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse((String) "postgres:18"));

  private final AppUserRepository users;
  private final SigningKeyRepository signingKeys;
  private final RegisteredClientRepository clients;
  private final PasswordEncoder passwordEncoder;
  private final ActiveRegisteredClientRepository clientStore;
  private final OAuthClientManagementService clientManagement;
  private final OAuthClientSynchronizer synchronizer;
  private final JdbcOperations jdbc;
  private final MockMvc mvc;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> ((PostgreSQLContainer) POSTGRES).getJdbcUrl());
    registry.add(
        "spring.datasource.username", () -> ((PostgreSQLContainer) POSTGRES).getUsername());
    registry.add(
        "spring.datasource.password", () -> ((PostgreSQLContainer) POSTGRES).getPassword());
  }

  PersistenceIntegrationTest(
      AppUserRepository users,
      SigningKeyRepository signingKeys,
      RegisteredClientRepository clients,
      PasswordEncoder passwordEncoder,
      ActiveRegisteredClientRepository clientStore,
      OAuthClientManagementService clientManagement,
      OAuthClientSynchronizer synchronizer,
      JdbcOperations jdbc,
      MockMvc mvc) {
    this.users = users;
    this.signingKeys = signingKeys;
    this.clients = clients;
    this.passwordEncoder = passwordEncoder;
    this.clientStore = clientStore;
    this.clientManagement = clientManagement;
    this.synchronizer = synchronizer;
    this.jdbc = jdbc;
    this.mvc = mvc;
  }

  @Test
  @Order(value = 1)
  void flywayAndJpaPersistenceStartTogether() {
    AppUser user = (AppUser) this.users.findById("developer").orElseThrow();
    RegisteredClient browserClient = this.clients.findByClientId("environment-client");
    RegisteredClient serviceClient = this.clients.findByClientId("service-client");
    Assertions.assertThat(user.getAuthorities())
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    Assertions.assertThat(
            (boolean) this.passwordEncoder.matches("test-password", user.getPassword()))
        .isTrue();
    Assertions.assertThat((boolean) this.signingKeys.existsByActiveTrue()).isTrue();
    Assertions.assertThat(browserClient).isNotNull();
    Assertions.assertThat((String) browserClient.getClientId()).isEqualTo("environment-client");
    Assertions.assertThat(
            (boolean)
                this.passwordEncoder.matches("environment-secret", browserClient.getClientSecret()))
        .isTrue();
    Assertions.assertThat(browserClient.getRedirectUris())
        .containsExactly(new String[] {"http://127.0.0.1:8080/callback"});
    Assertions.assertThat((boolean) browserClient.getClientSettings().isRequireProofKey()).isTrue();
    Assertions.assertThat(browserClient.getScopes())
        .contains(
            new String[] {"openid", "profile", "email", "api.read", "api.admin", "offline_access"});
    Assertions.assertThat(serviceClient).isNotNull();
    Assertions.assertThat(serviceClient.getAuthorizationGrantTypes())
        .containsExactly(new AuthorizationGrantType[] {AuthorizationGrantType.CLIENT_CREDENTIALS});
    Assertions.assertThat(serviceClient.getScopes()).containsExactly(new String[] {"api.read"});
  }

  @Test
  @Order(value = 2)
  void securityChainsRouteAuthorizationApiAndBrowserRequestsByPrecedence() throws Exception {
    this.mvc
        .perform(
            (RequestBuilder)
                MockMvcRequestBuilders.get(
                    (String) "/.well-known/openid-configuration", new Object[0]))
        .andExpect(MockMvcResultMatchers.status().isOk());
    this.mvc
        .perform((RequestBuilder) MockMvcRequestBuilders.get((String) "/api/public", new Object[0]))
        .andExpect(MockMvcResultMatchers.status().isOk());
    this.mvc
        .perform((RequestBuilder) MockMvcRequestBuilders.get((String) "/api/me", new Object[0]))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    this.mvc
        .perform((RequestBuilder) MockMvcRequestBuilders.get((String) "/login", new Object[0]))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }

  @Test
  @Order(value = 3)
  void jwtRemainsValidWhileClientLifecycleBlocksNewTokenIssuance() throws Exception {
    RegisteredClient lifecycleClient =
        RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("lifecycle-client")
            .clientName("Lifecycle integration client")
            .clientSecret("{noop}lifecycle-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope("api.read")
            .scope("api.admin")
            .tokenSettings(TokenSettings.builder().build())
            .build();
    this.clientStore.saveFromConfiguration(lifecycleClient);
    String readToken = this.obtainToken("api.read", true);
    String adminToken = this.obtainToken("api.admin", true);
    Assertions.assertThat(
            (Integer)
                this.jdbc.queryForObject(
                    "select count(*) from oauth2_authorization where registered_client_id = ?",
                    Integer.class,
                    lifecycleClient.getId()))
        .isEqualTo(2);
    String listBody =
        this.mvc
            .perform(
                (RequestBuilder)
                    MockMvcRequestBuilders.get((String) "/api/admin/oauth2-clients", new Object[0])
                        .header("Authorization", new Object[] {"Bearer " + adminToken}))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Assertions.assertThat((String) listBody)
        .doesNotContain(new CharSequence[] {"lifecycle-secret", "clientSecret"});
    this.mvc
        .perform(
            (RequestBuilder)
                MockMvcRequestBuilders.get((String) "/api/admin/oauth2-clients", new Object[0])
                    .header("Authorization", new Object[] {"Bearer " + readToken}))
        .andExpect(MockMvcResultMatchers.status().isForbidden());
    this.clientStore.disableClientsNotIn(Set.of());
    Assertions.assertThat(this.clientStore.findByClientId("lifecycle-client")).isNull();
    this.assertApiAccepts(readToken);
    this.obtainToken("api.read", false);
    this.mvc
        .perform(
            (RequestBuilder)
                MockMvcRequestBuilders.get((String) "/api/me", new Object[0])
                    .header("Authorization", new Object[] {"Bearer " + this.expiredToken()}))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    this.clientStore.enableManually("lifecycle-client");
    this.obtainToken("api.read", true);
    DeletionConfirmation confirmation =
        this.clientManagement.requestDeletion("lifecycle-client", "administrator");
    this.clientManagement.delete(
        "lifecycle-client", confirmation.confirmationToken(), "administrator");
    Assertions.assertThat(
            (Integer)
                this.jdbc.queryForObject(
                    "select count(*) from oauth2_authorization where registered_client_id = ?",
                    Integer.class,
                    lifecycleClient.getId()))
        .isZero();
    this.assertApiAccepts(readToken);
    this.obtainToken("api.read", false);
    Assertions.assertThatThrownBy(
            () ->
                this.clientManagement.delete(
                    "lifecycle-client", confirmation.confirmationToken(), "administrator"))
        .isInstanceOf(InvalidDeletionConfirmationException.class);
    OAuth2AuthorizationServerProperties reentry = new OAuth2AuthorizationServerProperties();
    reentry.getClient().put("lifecycle", PersistenceIntegrationTest.lifecycleConfiguration());
    this.synchronizer.synchronize(reentry);
    Assertions.assertThat(this.clientStore.findByClientId("lifecycle-client")).isNotNull();
    this.obtainToken("api.read", true);
  }

  @Test
  @Order(value = 4)
  void invalidMultiClientSynchronizationRollsBackEarlierUpserts() {
    OAuth2AuthorizationServerProperties invalid = new OAuth2AuthorizationServerProperties();
    OAuth2AuthorizationServerProperties.Client validClient =
        PersistenceIntegrationTest.lifecycleConfiguration();
    validClient.getRegistration().setClientId("rollback-client");
    OAuth2AuthorizationServerProperties.Client invalidClient =
        PersistenceIntegrationTest.lifecycleConfiguration();
    invalidClient.getRegistration().setClientId("invalid-client");
    invalidClient.setTokenEndpointAuthenticationSigningAlgorithm("not-an-algorithm");
    invalid.getClient().put("first-valid", validClient);
    invalid.getClient().put("second-invalid", invalidClient);
    Assertions.assertThatThrownBy(() -> this.synchronizer.synchronize(invalid))
        .isInstanceOf(IllegalArgumentException.class);
    Assertions.assertThat(this.clientStore.findIncludingInactiveByClientId("rollback-client"))
        .isNull();
    Assertions.assertThat(this.clientStore.findIncludingInactiveByClientId("invalid-client"))
        .isNull();
  }

  private String obtainToken(String scope, boolean successful) throws Exception {
    MvcResult result =
        this.mvc
            .perform(
                (RequestBuilder)
                    ((MockHttpServletRequestBuilder)
                            ((MockHttpServletRequestBuilder)
                                    ((MockHttpServletRequestBuilder)
                                            MockMvcRequestBuilders.post(
                                                    (String) "/oauth2/token", new Object[0])
                                                .with(
                                                    SecurityMockMvcRequestPostProcessors.httpBasic(
                                                        (String) "lifecycle-client",
                                                        (String) "lifecycle-secret")))
                                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                                .param("grant_type", new String[] {"client_credentials"}))
                        .param("scope", new String[] {scope}))
            .andExpect(
                successful
                    ? MockMvcResultMatchers.status().isOk()
                    : MockMvcResultMatchers.status().isUnauthorized())
            .andReturn();
    return successful
        ? (String)
            JsonPath.read(
                (String) result.getResponse().getContentAsString(),
                (String) "$.access_token",
                (Predicate[]) new Predicate[0])
        : null;
  }

  private void assertApiAccepts(String token) throws Exception {
    this.mvc
        .perform(
            (RequestBuilder)
                MockMvcRequestBuilders.get((String) "/api/me", new Object[0])
                    .header("Authorization", new Object[] {"Bearer " + token}))
        .andExpect(MockMvcResultMatchers.status().isOk());
  }

  private String expiredToken() throws Exception {
    RSAKey key =
        (RSAKey)
            JWK.parse(((SigningKey) this.signingKeys.findAllByActiveTrue().getFirst()).getJwk());
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .subject("expired-user")
            .issuer("http://localhost:9000")
            .issueTime(Date.from(now.minusSeconds(120L)))
            .expirationTime(Date.from(now.minusSeconds(60L)))
            .claim("scope", "api.read")
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static OAuth2AuthorizationServerProperties.Client lifecycleConfiguration() {
    OAuth2AuthorizationServerProperties.Client client =
        new OAuth2AuthorizationServerProperties.Client();
    client.getRegistration().setClientId("lifecycle-client");
    client.getRegistration().setClientName("Lifecycle integration client");
    client.getRegistration().setClientSecret("{noop}lifecycle-secret");
    client.getRegistration().setClientAuthenticationMethods(Set.of("client_secret_basic"));
    client.getRegistration().setAuthorizationGrantTypes(Set.of("client_credentials"));
    client.getRegistration().setScopes(Set.of("api.read", "api.admin"));
    return client;
  }
}

package com.taskmigo.console.access.internal.application.oauth.client;

import com.taskmigo.console.access.internal.persistence.oauth.client.ActiveRegisteredClientRepository;
import java.util.Set;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class OAuthClientSynchronizerTest {
  private final ActiveRegisteredClientRepository clients =
      (ActiveRegisteredClientRepository) Mockito.mock(ActiveRegisteredClientRepository.class);
  private final SpringRegisteredClientMapper mapper =
      (SpringRegisteredClientMapper) Mockito.mock(SpringRegisteredClientMapper.class);
  private final OAuthClientSynchronizer synchronizer =
      new OAuthClientSynchronizer(this.clients, this.mapper);

  OAuthClientSynchronizerTest() {}

  @Test
  void rejectsEmptyConfigurationBeforeWriting() {
    OAuth2AuthorizationServerProperties properties = new OAuth2AuthorizationServerProperties();
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> this.synchronizer.synchronize(properties))
                .isInstanceOf(IllegalArgumentException.class))
        .hasMessageContaining("At least one");
    Mockito.verifyNoInteractions(new Object[] {this.clients, this.mapper});
  }

  @Test
  void insertsMultipleClientsAndDisablesOnlyUnconfiguredRecords() {
    OAuth2AuthorizationServerProperties properties = new OAuth2AuthorizationServerProperties();
    OAuth2AuthorizationServerProperties.Client browser =
        SpringRegisteredClientMapperTest.client("browser", "{noop}browser");
    OAuth2AuthorizationServerProperties.Client worker =
        SpringRegisteredClientMapperTest.client("worker", "{noop}worker");
    properties.getClient().put("browser-registration", browser);
    properties.getClient().put("worker-registration", worker);
    RegisteredClient mappedBrowser =
        OAuthClientSynchronizerTest.registeredClient("browser-id", "browser");
    RegisteredClient mappedWorker =
        OAuthClientSynchronizerTest.registeredClient("worker-id", "worker");
    Mockito.when(this.mapper.map("browser-registration", browser, null)).thenReturn(mappedBrowser);
    Mockito.when(this.mapper.map("worker-registration", worker, null)).thenReturn(mappedWorker);
    this.synchronizer.synchronize(properties);
    ((ActiveRegisteredClientRepository) Mockito.verify(this.clients))
        .saveFromConfiguration(mappedBrowser);
    ((ActiveRegisteredClientRepository) Mockito.verify(this.clients))
        .saveFromConfiguration(mappedWorker);
    ((ActiveRegisteredClientRepository) Mockito.verify(this.clients))
        .disableClientsNotIn(Set.of("browser-id", "worker-id"));
  }

  @Test
  void reusesInactiveClientDuringUpdate() {
    OAuth2AuthorizationServerProperties properties = new OAuth2AuthorizationServerProperties();
    OAuth2AuthorizationServerProperties.Client configured =
        SpringRegisteredClientMapperTest.client("browser", "{noop}browser");
    properties.getClient().put("browser-registration", configured);
    RegisteredClient existing =
        OAuthClientSynchronizerTest.registeredClient("existing-id", "browser");
    RegisteredClient mapped =
        OAuthClientSynchronizerTest.registeredClient("existing-id", "browser");
    Mockito.when(this.clients.findIncludingInactiveByClientId("browser")).thenReturn(existing);
    Mockito.when(this.mapper.map("browser-registration", configured, existing)).thenReturn(mapped);
    this.synchronizer.synchronize(properties);
    ((ActiveRegisteredClientRepository) Mockito.verify(this.clients)).saveFromConfiguration(mapped);
    ((ActiveRegisteredClientRepository) Mockito.verify(this.clients))
        .disableClientsNotIn(Set.of("existing-id"));
  }

  private static RegisteredClient registeredClient(String id, String clientId) {
    return RegisteredClient.withId(id)
        .clientId(clientId)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("api.read")
        .build();
  }
}

package com.taskmigo.console.access.internal.application.oauth.client;

import com.taskmigo.console.access.internal.persistence.oauth.client.ActiveRegisteredClientRepository;
import java.util.HashSet;
import java.util.Map;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
public class OAuthClientSynchronizer {
  private final ActiveRegisteredClientRepository clients;
  private final SpringRegisteredClientMapper mapper;

  public OAuthClientSynchronizer(
      ActiveRegisteredClientRepository clients, SpringRegisteredClientMapper mapper) {
    this.clients = clients;
    this.mapper = mapper;
  }

  @Transactional
  public void synchronize(OAuth2AuthorizationServerProperties properties) {
    Map<String, OAuth2AuthorizationServerProperties.Client> configuredClients =
        properties.getClient();
    Assert.notEmpty(configuredClients, "At least one OAuth client must be configured");
    HashSet<String> configuredRegisteredClientIds = new HashSet<String>();
    configuredClients.forEach(
        (registrationId, configuredClient) -> {
          String clientId = configuredClient.getRegistration().getClientId();
          RegisteredClient existing = this.clients.findIncludingInactiveByClientId(clientId);
          RegisteredClient mapped = this.mapper.map(registrationId, configuredClient, existing);
          this.clients.saveFromConfiguration(mapped);
          configuredRegisteredClientIds.add(mapped.getId());
        });
    this.clients.disableClientsNotIn(configuredRegisteredClientIds);
  }
}

package com.taskmigo.console.access.internal.application.oauth.management;

import com.taskmigo.console.access.internal.domain.oauth.client.ClientDeletionConfirmation;
import com.taskmigo.console.access.internal.domain.oauth.client.StoredClient;
import com.taskmigo.console.access.internal.persistence.oauth.client.ActiveRegisteredClientRepository;
import com.taskmigo.console.access.internal.persistence.oauth.client.ClientDeletionConfirmationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthClientManagementService {
  static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5L);
  private static final Logger LOGGER = LoggerFactory.getLogger(OAuthClientManagementService.class);
  private final ActiveRegisteredClientRepository clients;
  private final ClientDeletionConfirmationRepository confirmations;
  private final OAuth2AuthorizationServerProperties authorizationServerProperties;
  private final DeletionTokenGenerator tokenGenerator;
  private final Clock clock;

  public OAuthClientManagementService(
      ActiveRegisteredClientRepository clients,
      ClientDeletionConfirmationRepository confirmations,
      OAuth2AuthorizationServerProperties authorizationServerProperties,
      DeletionTokenGenerator tokenGenerator,
      Clock clock) {
    this.clients = clients;
    this.confirmations = confirmations;
    this.authorizationServerProperties = authorizationServerProperties;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<OAuthClientView> list() {
    Set<String> configuredClientIds = this.configuredClientIds();
    return this.clients.findAllIncludingInactive().stream()
        .map(client -> OAuthClientManagementService.toView(client, configuredClientIds))
        .sorted((left, right) -> left.clientId().compareTo(right.clientId()))
        .toList();
  }

  @Transactional
  public OAuthClientView enable(String clientId, String requestedBy) {
    StoredClient enabled = this.clients.enableManually(clientId);
    if (enabled == null) {
      throw new OAuthClientNotFoundException(clientId);
    }
    LOGGER.info("OAuth client {} manually enabled by {}", clientId, requestedBy);
    return OAuthClientManagementService.toView(enabled, this.configuredClientIds());
  }

  @Transactional
  public DeletionConfirmation requestDeletion(String clientId, String requestedBy) {
    this.assertNotConfigured(clientId);
    RegisteredClient client = this.clients.findIncludingInactiveByClientId(clientId);
    if (client == null) {
      throw new OAuthClientNotFoundException(clientId);
    }
    Instant now = this.clock.instant();
    this.confirmations.deleteAllByExpiresAtBefore(now);
    this.confirmations.deleteAllByRegisteredClientId(client.getId());
    String token = this.tokenGenerator.generate();
    Instant expiresAt = now.plus(CONFIRMATION_TTL);
    this.confirmations.save(
        new ClientDeletionConfirmation(
            UUID.randomUUID(),
            client.getId(),
            OAuthClientManagementService.hash(token),
            requestedBy,
            expiresAt,
            now));
    LOGGER.info("OAuth client deletion confirmation requested for {} by {}", clientId, requestedBy);
    return new DeletionConfirmation(clientId, token, expiresAt);
  }

  @Transactional
  public void delete(String clientId, String confirmationToken, String requestedBy) {
    Instant now = this.clock.instant();
    ClientDeletionConfirmation confirmation =
        this.confirmations
            .findByTokenHash(OAuthClientManagementService.hash(confirmationToken))
            .orElseThrow(InvalidDeletionConfirmationException::new);
    if (confirmation.isUsed()
        || !confirmation.getExpiresAt().isAfter(now)
        || !confirmation.getRequestedBy().equals(requestedBy)) {
      throw new InvalidDeletionConfirmationException();
    }
    RegisteredClient client =
        this.clients.findIncludingInactiveById(confirmation.getRegisteredClientId());
    if (client == null) {
      throw new OAuthClientNotFoundException(clientId);
    }
    if (!client.getClientId().equals(clientId)) {
      throw new InvalidDeletionConfirmationException();
    }
    this.assertNotConfigured(clientId);
    confirmation.markUsed(now);
    this.confirmations.save(confirmation);
    this.clients.deleteByRegisteredClientId(client.getId());
    LOGGER.info("OAuth client {} permanently deleted by {}", clientId, requestedBy);
  }

  private void assertNotConfigured(String clientId) {
    if (this.configuredClientIds().contains(clientId)) {
      throw new ClientStillConfiguredException(clientId);
    }
  }

  private Set<String> configuredClientIds() {
    return this.authorizationServerProperties.getClient().values().stream()
        .map(client -> client.getRegistration().getClientId())
        .collect(Collectors.toUnmodifiableSet());
  }

  private static OAuthClientView toView(StoredClient stored, Set<String> configuredClientIds) {
    RegisteredClient client = stored.client();
    return new OAuthClientView(
        client.getClientId(),
        client.getClientName(),
        stored.active(),
        configuredClientIds.contains(client.getClientId()),
        stored.manualOverride(),
        stored.updatedAt());
  }

  private static String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}

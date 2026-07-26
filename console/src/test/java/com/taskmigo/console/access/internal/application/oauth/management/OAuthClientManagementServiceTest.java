package com.taskmigo.console.access.internal.application.oauth.management;

import com.taskmigo.console.access.internal.domain.oauth.client.ClientDeletionConfirmation;
import com.taskmigo.console.access.internal.domain.oauth.client.StoredClient;
import com.taskmigo.console.access.internal.persistence.oauth.client.ActiveRegisteredClientRepository;
import com.taskmigo.console.access.internal.persistence.oauth.client.ClientDeletionConfirmationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.verification.VerificationMode;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

class OAuthClientManagementServiceTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final ActiveRegisteredClientRepository clients =
      (ActiveRegisteredClientRepository) Mockito.mock(ActiveRegisteredClientRepository.class);
  private final ClientDeletionConfirmationRepository confirmations =
      (ClientDeletionConfirmationRepository)
          Mockito.mock(ClientDeletionConfirmationRepository.class);
  private final OAuth2AuthorizationServerProperties properties =
      new OAuth2AuthorizationServerProperties();
  private final DeletionTokenGenerator tokenGenerator =
      (DeletionTokenGenerator) Mockito.mock(DeletionTokenGenerator.class);
  private final OAuthClientManagementService service =
      new OAuthClientManagementService(
          this.clients,
          this.confirmations,
          this.properties,
          this.tokenGenerator,
          Clock.fixed(NOW, ZoneOffset.UTC));

  OAuthClientManagementServiceTest() {}

  @BeforeEach
  void configureBrowserClient() {
    OAuth2AuthorizationServerProperties.Client configured =
        new OAuth2AuthorizationServerProperties.Client();
    configured.getRegistration().setClientId("configured-client");
    configured.getRegistration().setClientSecret("{noop}secret");
    configured.getRegistration().setClientAuthenticationMethods(Set.of("client_secret_basic"));
    configured.getRegistration().setAuthorizationGrantTypes(Set.of("client_credentials"));
    configured.getRegistration().setScopes(Set.of("api.admin"));
    this.properties.getClient().put("browser", configured);
  }

  @Test
  void listsClientsWithoutExposingSecretAndMarksConfigurationState() {
    Mockito.when(this.clients.findAllIncludingInactive())
        .thenReturn(
            List.of(
                OAuthClientManagementServiceTest.stored("2", "other-client", false, false),
                OAuthClientManagementServiceTest.stored("1", "configured-client", true, false)));
    List<OAuthClientView> result = this.service.list();
    Assertions.assertThat(result)
        .extracting(OAuthClientView::clientId)
        .containsExactly(new String[] {"configured-client", "other-client"});
    Assertions.assertThat((boolean) ((OAuthClientView) result.getFirst()).configured()).isTrue();
    Assertions.assertThat((boolean) ((OAuthClientView) result.getLast()).configured()).isFalse();
  }

  @Test
  void enablesMissingAndExistingClients() {
    Mockito.when(this.clients.enableManually("missing")).thenReturn(null);
    Assertions.assertThatThrownBy(() -> this.service.enable("missing", "admin"))
        .isInstanceOf(OAuthClientNotFoundException.class);
    StoredClient enabled = OAuthClientManagementServiceTest.stored("2", "other-client", true, true);
    Mockito.when(this.clients.enableManually("other-client")).thenReturn(enabled);
    OAuthClientView result = this.service.enable("other-client", "admin");
    Assertions.assertThat((boolean) result.active()).isTrue();
    Assertions.assertThat((boolean) result.manualOverride()).isTrue();
  }

  @Test
  void rejectsDeletionRequestForConfiguredOrMissingClient() {
    Assertions.assertThatThrownBy(() -> this.service.requestDeletion("configured-client", "admin"))
        .isInstanceOf(ClientStillConfiguredException.class);
    Mockito.when(this.clients.findIncludingInactiveByClientId("missing")).thenReturn(null);
    Assertions.assertThatThrownBy(() -> this.service.requestDeletion("missing", "admin"))
        .isInstanceOf(OAuthClientNotFoundException.class);
  }

  @Test
  void replacesPriorConfirmationAndReturnsRawTokenOnce() {
    RegisteredClient client = OAuthClientManagementServiceTest.client("2", "other-client");
    Mockito.when(this.clients.findIncludingInactiveByClientId("other-client")).thenReturn(client);
    Mockito.when(this.tokenGenerator.generate()).thenReturn("raw-token");
    DeletionConfirmation result = this.service.requestDeletion("other-client", "admin");
    ((ClientDeletionConfirmationRepository) Mockito.verify(this.confirmations))
        .deleteAllByExpiresAtBefore(NOW);
    ((ClientDeletionConfirmationRepository) Mockito.verify(this.confirmations))
        .deleteAllByRegisteredClientId("2");
    ArgumentCaptor saved = ArgumentCaptor.forClass(ClientDeletionConfirmation.class);
    ((ClientDeletionConfirmationRepository) Mockito.verify(this.confirmations))
        .save(((ClientDeletionConfirmation) saved.capture()));
    Assertions.assertThat(
            (String) ((ClientDeletionConfirmation) saved.getValue()).getRegisteredClientId())
        .isEqualTo("2");
    Assertions.assertThat((String) ((ClientDeletionConfirmation) saved.getValue()).getRequestedBy())
        .isEqualTo("admin");
    Assertions.assertThat((String) result.confirmationToken()).isEqualTo("raw-token");
    Assertions.assertThat((Instant) result.expiresAt())
        .isEqualTo(NOW.plus(OAuthClientManagementService.CONFIRMATION_TTL));
  }

  @Test
  void permanentlyDeletesWithValidConfirmation() {
    ClientDeletionConfirmation confirmation =
        OAuthClientManagementServiceTest.confirmation(
            "raw-token", "2", "admin", NOW.plusSeconds(60L));
    Mockito.when(
            this.confirmations.findByTokenHash(OAuthClientManagementServiceTest.hash("raw-token")))
        .thenReturn(Optional.of(confirmation));
    Mockito.when(this.clients.findIncludingInactiveById("2"))
        .thenReturn(OAuthClientManagementServiceTest.client("2", "other-client"));
    this.service.delete("other-client", "raw-token", "admin");
    Assertions.assertThat((boolean) confirmation.isUsed()).isTrue();
    ((ClientDeletionConfirmationRepository) Mockito.verify(this.confirmations)).save(confirmation);
    ((ActiveRegisteredClientRepository) Mockito.verify(this.clients))
        .deleteByRegisteredClientId("2");
  }

  @Test
  void rejectsMalformedExpiredUsedWrongRequesterAndWrongClientConfirmations() {
    Mockito.when(
            this.confirmations.findByTokenHash(OAuthClientManagementServiceTest.hash("missing")))
        .thenReturn(Optional.empty());
    Assertions.assertThatThrownBy(() -> this.service.delete("other-client", "missing", "admin"))
        .isInstanceOf(InvalidDeletionConfirmationException.class);
    this.assertInvalid(
        OAuthClientManagementServiceTest.confirmation("expired", "2", "admin", NOW),
        "expired",
        "admin");
    ClientDeletionConfirmation used =
        OAuthClientManagementServiceTest.confirmation("used", "2", "admin", NOW.plusSeconds(60L));
    used.markUsed(NOW.minusSeconds(1L));
    this.assertInvalid(used, "used", "admin");
    this.assertInvalid(
        OAuthClientManagementServiceTest.confirmation(
            "requester", "2", "another-admin", NOW.plusSeconds(60L)),
        "requester",
        "admin");
    ClientDeletionConfirmation wrongClient =
        OAuthClientManagementServiceTest.confirmation(
            "wrong-client", "2", "admin", NOW.plusSeconds(60L));
    Mockito.when(
            this.confirmations.findByTokenHash(
                OAuthClientManagementServiceTest.hash("wrong-client")))
        .thenReturn(Optional.of(wrongClient));
    Mockito.when(this.clients.findIncludingInactiveById("2"))
        .thenReturn(OAuthClientManagementServiceTest.client("2", "different-client"));
    Assertions.assertThatThrownBy(
            () -> this.service.delete("other-client", "wrong-client", "admin"))
        .isInstanceOf(InvalidDeletionConfirmationException.class);
  }

  @Test
  void rejectsDeleteWhenClientIsStillConfiguredOrNoLongerExists() {
    ClientDeletionConfirmation configured =
        OAuthClientManagementServiceTest.confirmation(
            "configured", "1", "admin", NOW.plusSeconds(60L));
    Mockito.when(
            this.confirmations.findByTokenHash(OAuthClientManagementServiceTest.hash("configured")))
        .thenReturn(Optional.of(configured));
    Mockito.when(this.clients.findIncludingInactiveById("1"))
        .thenReturn(OAuthClientManagementServiceTest.client("1", "configured-client"));
    Assertions.assertThatThrownBy(
            () -> this.service.delete("configured-client", "configured", "admin"))
        .isInstanceOf(ClientStillConfiguredException.class);
    ((ActiveRegisteredClientRepository)
            Mockito.verify(this.clients, (VerificationMode) Mockito.never()))
        .deleteByRegisteredClientId("1");
    ClientDeletionConfirmation missing =
        OAuthClientManagementServiceTest.confirmation("gone", "9", "admin", NOW.plusSeconds(60L));
    Mockito.when(this.confirmations.findByTokenHash(OAuthClientManagementServiceTest.hash("gone")))
        .thenReturn(Optional.of(missing));
    Mockito.when(this.clients.findIncludingInactiveById("9")).thenReturn(null);
    Assertions.assertThatThrownBy(() -> this.service.delete("gone", "gone", "admin"))
        .isInstanceOf(OAuthClientNotFoundException.class);
  }

  private void assertInvalid(
      ClientDeletionConfirmation confirmation, String token, String requester) {
    Mockito.when(this.confirmations.findByTokenHash(OAuthClientManagementServiceTest.hash(token)))
        .thenReturn(Optional.of(confirmation));
    Assertions.assertThatThrownBy(() -> this.service.delete("other-client", token, requester))
        .isInstanceOf(InvalidDeletionConfirmationException.class);
  }

  private static ClientDeletionConfirmation confirmation(
      String token, String clientId, String requester, Instant expiresAt) {
    return new ClientDeletionConfirmation(
        UUID.randomUUID(),
        clientId,
        OAuthClientManagementServiceTest.hash(token),
        requester,
        expiresAt,
        NOW);
  }

  private static StoredClient stored(
      String id, String clientId, boolean active, boolean manualOverride) {
    return new StoredClient(
        OAuthClientManagementServiceTest.client(id, clientId), active, manualOverride, NOW);
  }

  private static RegisteredClient client(String id, String clientId) {
    return RegisteredClient.withId(id)
        .clientId(clientId)
        .clientSecret("{noop}secret")
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
        .scope("api.read")
        .build();
  }

  private static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}

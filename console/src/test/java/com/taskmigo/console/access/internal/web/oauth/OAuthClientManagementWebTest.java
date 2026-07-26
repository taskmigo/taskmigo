package com.taskmigo.console.access.internal.web.oauth;

import com.taskmigo.console.access.internal.application.oauth.management.ClientStillConfiguredException;
import com.taskmigo.console.access.internal.application.oauth.management.DeletionConfirmation;
import com.taskmigo.console.access.internal.application.oauth.management.InvalidDeletionConfirmationException;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientManagementService;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientNotFoundException;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientView;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;

class OAuthClientManagementWebTest {
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final OAuthClientManagementService service =
      (OAuthClientManagementService) Mockito.mock(OAuthClientManagementService.class);
  private final OAuthClientManagementController controller =
      new OAuthClientManagementController(this.service);
  private final OAuthClientManagementExceptionHandler errors =
      new OAuthClientManagementExceptionHandler();
  private final Jwt administrator =
      Jwt.withTokenValue("token")
          .header("alg", "none")
          .subject("administrator")
          .issuedAt(NOW)
          .expiresAt(NOW.plusSeconds(300L))
          .build();

  OAuthClientManagementWebTest() {}

  @Test
  void delegatesEveryEndpointAndReturnsContractStatuses() {
    OAuthClientView view = new OAuthClientView("client", "Client", true, false, true, NOW);
    DeletionConfirmation confirmation =
        new DeletionConfirmation("client", "raw-token", NOW.plusSeconds(300L));
    Mockito.when(this.service.list()).thenReturn(List.of(view));
    Mockito.when(this.service.enable("client", "administrator")).thenReturn(view);
    Mockito.when(this.service.requestDeletion("client", "administrator")).thenReturn(confirmation);
    Assertions.assertThat(this.controller.list()).containsExactly(new OAuthClientView[] {view});
    Assertions.assertThat(this.controller.enable("client", this.administrator)).isEqualTo(view);
    Assertions.assertThat(
            this.controller.requestDeletion("client", this.administrator).getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    Assertions.assertThat(
            this.controller.delete("client", "raw-token", this.administrator).getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    ((OAuthClientManagementService) Mockito.verify(this.service))
        .delete("client", "raw-token", "administrator");
  }

  @Test
  void mapsLifecycleFailuresToStableProblemCodes() {
    ProblemDetail missing = this.errors.notFound(new OAuthClientNotFoundException("missing"));
    ProblemDetail configured =
        this.errors.stillConfigured(new ClientStillConfiguredException("configured"));
    ProblemDetail invalid =
        this.errors.invalidConfirmation(new InvalidDeletionConfirmationException());
    Assertions.assertThat((int) missing.getStatus()).isEqualTo(404);
    Assertions.assertThat(missing.getProperties()).containsEntry("code", "oauth_client_not_found");
    Assertions.assertThat((int) configured.getStatus()).isEqualTo(409);
    Assertions.assertThat(configured.getProperties())
        .containsEntry("code", "client_still_configured");
    Assertions.assertThat((int) invalid.getStatus()).isEqualTo(409);
    Assertions.assertThat(invalid.getProperties())
        .containsEntry("code", "invalid_deletion_confirmation");
  }
}

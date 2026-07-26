package com.taskmigo.console.access.internal.web.oauth;

import com.taskmigo.console.access.internal.application.oauth.management.DeletionConfirmation;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientManagementService;
import com.taskmigo.console.access.internal.application.oauth.management.OAuthClientView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = {"/api/admin/oauth2-clients"})
@PreAuthorize(value = "hasAuthority('SCOPE_api.admin')")
public class OAuthClientManagementController {
  private final OAuthClientManagementService clients;

  public OAuthClientManagementController(OAuthClientManagementService clients) {
    this.clients = clients;
  }

  @GetMapping
  public List<OAuthClientView> list() {
    return this.clients.list();
  }

  @PostMapping(value = {"/{clientId}/enable"})
  public OAuthClientView enable(
      @PathVariable String clientId, @AuthenticationPrincipal Jwt principal) {
    return this.clients.enable(clientId, principal.getSubject());
  }

  @PostMapping(value = {"/{clientId}/deletion-confirmations"})
  public ResponseEntity<DeletionConfirmation> requestDeletion(
      @PathVariable String clientId, @AuthenticationPrincipal Jwt principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(this.clients.requestDeletion(clientId, principal.getSubject()));
  }

  @DeleteMapping(value = {"/{clientId}"})
  public ResponseEntity<Void> delete(
      @PathVariable String clientId,
      @RequestHeader(value = "X-Deletion-Confirmation") String confirmationToken,
      @AuthenticationPrincipal Jwt principal) {
    this.clients.delete(clientId, confirmationToken, principal.getSubject());
    return ResponseEntity.noContent().build();
  }
}

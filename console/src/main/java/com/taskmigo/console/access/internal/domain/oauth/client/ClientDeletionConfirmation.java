package com.taskmigo.console.access.internal.domain.oauth.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "oauth2_client_deletion_confirmation")
public class ClientDeletionConfirmation {
  @Id private UUID id;

  @Column(name = "registered_client_id", nullable = false, length = 100)
  private String registeredClientId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "requested_by", nullable = false, length = 200)
  private String requestedBy;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "used_at")
  private Instant usedAt;

  protected ClientDeletionConfirmation() {}

  public ClientDeletionConfirmation(
      UUID id,
      String registeredClientId,
      String tokenHash,
      String requestedBy,
      Instant expiresAt,
      Instant createdAt) {
    this.id = id;
    this.registeredClientId = registeredClientId;
    this.tokenHash = tokenHash;
    this.requestedBy = requestedBy;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
  }

  public String getRegisteredClientId() {
    return this.registeredClientId;
  }

  public String getRequestedBy() {
    return this.requestedBy;
  }

  public Instant getExpiresAt() {
    return this.expiresAt;
  }

  public boolean isUsed() {
    return this.usedAt != null;
  }

  public void markUsed(Instant now) {
    this.usedAt = now;
  }
}

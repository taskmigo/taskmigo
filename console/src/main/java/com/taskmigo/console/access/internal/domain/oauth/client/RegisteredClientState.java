package com.taskmigo.console.access.internal.domain.oauth.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "oauth2_registered_client_state")
public class RegisteredClientState {
  @Id
  @Column(name = "registered_client_id", length = 100)
  private String registeredClientId;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "manual_override", nullable = false)
  private boolean manualOverride;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RegisteredClientState() {}

  public RegisteredClientState(String registeredClientId, Instant now) {
    this.registeredClientId = registeredClientId;
    this.active = true;
    this.updatedAt = now;
  }

  public String getRegisteredClientId() {
    return this.registeredClientId;
  }

  public boolean isActive() {
    return this.active;
  }

  public boolean isManualOverride() {
    return this.manualOverride;
  }

  public Instant getUpdatedAt() {
    return this.updatedAt;
  }

  public void activateFromConfiguration(Instant now) {
    this.active = true;
    this.manualOverride = false;
    this.updatedAt = now;
  }

  public void enableManually(Instant now) {
    this.active = true;
    this.manualOverride = true;
    this.updatedAt = now;
  }

  public boolean disableIfNotManuallyEnabled(Instant now) {
    if (this.manualOverride || !this.active) {
      return false;
    }
    this.active = false;
    this.updatedAt = now;
    return true;
  }
}

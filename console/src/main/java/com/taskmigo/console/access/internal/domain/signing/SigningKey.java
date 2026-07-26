package com.taskmigo.console.access.internal.domain.signing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "oauth2_signing_key")
public class SigningKey {
  @Id
  @Column(name = "key_id", length = 100)
  private String keyId;

  @Column(nullable = false, columnDefinition = "text")
  private String jwk;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
  private Instant createdAt;

  protected SigningKey() {}

  public SigningKey(String keyId, String jwk) {
    this.keyId = keyId;
    this.jwk = jwk;
    this.active = true;
  }

  public String getJwk() {
    return this.jwk;
  }
}

package com.taskmigo.console.access.internal.persistence.oauth.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "oauth2_registered_client")
public class RegisteredClientRecord {
  @Id
  @Column(length = 100)
  private String id;

  protected RegisteredClientRecord() {}
}

package com.taskmigo.console.access.internal.domain.identity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "app_user")
public class AppUser {
  @Id
  @Column(length = 100)
  private String username;

  @Column(nullable = false, length = 500)
  private String password;

  @Column(nullable = false)
  private boolean enabled;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "app_authority",
      joinColumns = {@JoinColumn(name = "username")})
  @Column(name = "authority", nullable = false, length = 100)
  private Set<String> authorities = new HashSet<String>();

  protected AppUser() {}

  public AppUser(String username, String password) {
    this.username = username;
    this.password = password;
    this.enabled = true;
  }

  public String getUsername() {
    return this.username;
  }

  public String getPassword() {
    return this.password;
  }

  public boolean isEnabled() {
    return this.enabled;
  }

  public Set<String> getAuthorities() {
    return Set.copyOf(this.authorities);
  }

  public void updateCredentials(String password) {
    this.password = password;
    this.enabled = true;
  }

  public void grantAuthority(String authority) {
    this.authorities.add(authority);
  }

  public void disable() {
    this.enabled = false;
  }
}

package com.taskmigo.console.access.internal.application.identity;

import com.taskmigo.console.access.internal.configuration.identity.IdentityProperties;
import com.taskmigo.console.access.internal.domain.identity.AppUser;
import com.taskmigo.console.access.internal.persistence.identity.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BootstrapAdministrator {
  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;

  public BootstrapAdministrator(AppUserRepository users, PasswordEncoder passwordEncoder) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void provision(IdentityProperties.Bootstrap bootstrap) {
    String encodedPassword = this.passwordEncoder.encode(bootstrap.password());
    AppUser user =
        this.users
            .findById(bootstrap.username())
            .orElseGet(() -> new AppUser(bootstrap.username(), encodedPassword));
    user.updateCredentials(encodedPassword);
    user.grantAuthority("ROLE_USER");
    user.grantAuthority("ROLE_ADMIN");
    this.users.save(user);
  }
}

package com.taskmigo.console.access.internal.application.identity;

import com.taskmigo.console.access.internal.domain.identity.AppUser;
import com.taskmigo.console.access.internal.persistence.identity.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JpaUserDetailsService implements UserDetailsService {
  private final AppUserRepository users;

  public JpaUserDetailsService(AppUserRepository users) {
    this.users = users;
  }

  @Override
  @Transactional(readOnly = true)
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    AppUser user =
        users
            .findById(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return User.withUsername(user.getUsername())
        .password(user.getPassword())
        .disabled(!user.isEnabled())
        .authorities(user.getAuthorities().toArray(String[]::new))
        .build();
  }
}

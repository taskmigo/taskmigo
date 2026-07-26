package com.taskmigo.console.access.internal.application.identity;

import com.taskmigo.console.access.internal.domain.identity.AppUser;
import com.taskmigo.console.access.internal.persistence.identity.AppUserRepository;
import java.util.Optional;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class JpaUserDetailsServiceTest {
  private final AppUserRepository users = (AppUserRepository) Mockito.mock(AppUserRepository.class);
  private final JpaUserDetailsService service = new JpaUserDetailsService(this.users);

  JpaUserDetailsServiceTest() {}

  @Test
  void mapsActiveUserAndAuthorities() {
    AppUser user = new AppUser("developer", "{noop}password");
    user.grantAuthority("ROLE_USER");
    Mockito.when(this.users.findById("developer")).thenReturn(Optional.of(user));
    UserDetails details = this.service.loadUserByUsername("developer");
    Assertions.assertThat((String) details.getUsername()).isEqualTo("developer");
    Assertions.assertThat((String) details.getPassword()).isEqualTo("{noop}password");
    Assertions.assertThat((boolean) details.isEnabled()).isTrue();
    Assertions.assertThat(details.getAuthorities())
        .extracting("authority")
        .containsExactly(new Object[] {"ROLE_USER"});
  }

  @Test
  void mapsDisabledUser() {
    AppUser user = new AppUser("disabled", "{noop}password");
    user.disable();
    Mockito.when(this.users.findById("disabled")).thenReturn(Optional.of(user));
    Assertions.assertThat((boolean) this.service.loadUserByUsername("disabled").isEnabled())
        .isFalse();
  }

  @Test
  void rejectsUnknownUser() {
    Mockito.when(this.users.findById("missing")).thenReturn(Optional.empty());
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> this.service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class))
        .hasMessageContaining("missing");
  }
}

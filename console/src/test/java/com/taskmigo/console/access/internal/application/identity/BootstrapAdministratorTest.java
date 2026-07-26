package com.taskmigo.console.access.internal.application.identity;

import com.taskmigo.console.access.internal.configuration.identity.IdentityProperties;
import com.taskmigo.console.access.internal.domain.identity.AppUser;
import com.taskmigo.console.access.internal.persistence.identity.AppUserRepository;
import java.util.Optional;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

class BootstrapAdministratorTest {
  private final AppUserRepository users = (AppUserRepository) Mockito.mock(AppUserRepository.class);
  private final PasswordEncoder encoder = (PasswordEncoder) Mockito.mock(PasswordEncoder.class);
  private final BootstrapAdministrator bootstrap =
      new BootstrapAdministrator(this.users, this.encoder);
  private final IdentityProperties.Bootstrap properties =
      new IdentityProperties.Bootstrap("developer", "raw-password");

  BootstrapAdministratorTest() {}

  @Test
  void createsEnabledAdministrator() {
    Mockito.when(this.users.findById("developer")).thenReturn(Optional.empty());
    Mockito.when(this.encoder.encode("raw-password")).thenReturn("encoded-password");
    this.bootstrap.provision(this.properties);
    ArgumentCaptor saved = ArgumentCaptor.forClass(AppUser.class);
    ((AppUserRepository) Mockito.verify(this.users)).save(((AppUser) saved.capture()));
    Assertions.assertThat((String) ((AppUser) saved.getValue()).getUsername())
        .isEqualTo("developer");
    Assertions.assertThat((String) ((AppUser) saved.getValue()).getPassword())
        .isEqualTo("encoded-password");
    Assertions.assertThat((boolean) ((AppUser) saved.getValue()).isEnabled()).isTrue();
    Assertions.assertThat(((AppUser) saved.getValue()).getAuthorities())
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
  }

  @Test
  void updatesReenablesAndPreservesExistingAuthorities() {
    AppUser existing = new AppUser("developer", "old");
    existing.grantAuthority("ROLE_AUDITOR");
    existing.disable();
    Mockito.when(this.users.findById("developer")).thenReturn(Optional.of(existing));
    Mockito.when(this.encoder.encode("raw-password")).thenReturn("encoded-password");
    this.bootstrap.provision(this.properties);
    Assertions.assertThat((String) existing.getPassword()).isEqualTo("encoded-password");
    Assertions.assertThat((boolean) existing.isEnabled()).isTrue();
    Assertions.assertThat(existing.getAuthorities())
        .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN", "ROLE_AUDITOR");
    ((AppUserRepository) Mockito.verify(this.users)).save(existing);
  }

  @Test
  void propagatesPasswordEncodingFailureWithoutSaving() {
    Mockito.when(this.encoder.encode("raw-password"))
        .thenThrow(new Throwable[] {new IllegalStateException("encoder failed")});
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> this.bootstrap.provision(this.properties))
                .isInstanceOf(IllegalStateException.class))
        .hasMessage("encoder failed");
  }

  @Test
  void propagatesRepositoryFailure() {
    Mockito.when(this.encoder.encode("raw-password")).thenReturn("encoded-password");
    Mockito.when(this.users.findById("developer"))
        .thenThrow(new Throwable[] {new IllegalStateException("database failed")});
    ((AbstractThrowableAssert)
            Assertions.assertThatThrownBy(() -> this.bootstrap.provision(this.properties))
                .isInstanceOf(IllegalStateException.class))
        .hasMessage("database failed");
  }
}

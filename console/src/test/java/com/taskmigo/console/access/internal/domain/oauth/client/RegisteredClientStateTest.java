package com.taskmigo.console.access.internal.domain.oauth.client;

import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class RegisteredClientStateTest {
  private static final Instant FIRST = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-01-02T00:00:00Z");

  RegisteredClientStateTest() {}

  @Test
  void configurationActivationClearsManualOverride() {
    RegisteredClientState state = new RegisteredClientState("client", FIRST);
    state.enableManually(FIRST);
    state.activateFromConfiguration(SECOND);
    Assertions.assertThat((boolean) state.isActive()).isTrue();
    Assertions.assertThat((boolean) state.isManualOverride()).isFalse();
    Assertions.assertThat((Instant) state.getUpdatedAt()).isEqualTo(SECOND);
  }

  @Test
  void manuallyEnabledClientIsNotDisabledBySynchronization() {
    RegisteredClientState state = new RegisteredClientState("client", FIRST);
    state.enableManually(FIRST);
    Assertions.assertThat((boolean) state.disableIfNotManuallyEnabled(SECOND)).isFalse();
    Assertions.assertThat((boolean) state.isActive()).isTrue();
  }

  @Test
  void configuredClientCanBeDisabledAndRepeatedDisableIsIdempotent() {
    RegisteredClientState state = new RegisteredClientState("client", FIRST);
    Assertions.assertThat((boolean) state.disableIfNotManuallyEnabled(SECOND)).isTrue();
    Assertions.assertThat((boolean) state.isActive()).isFalse();
    Assertions.assertThat((boolean) state.disableIfNotManuallyEnabled(SECOND.plusSeconds(1L)))
        .isFalse();
  }
}

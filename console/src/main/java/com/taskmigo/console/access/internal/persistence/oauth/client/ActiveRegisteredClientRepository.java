package com.taskmigo.console.access.internal.persistence.oauth.client;

import com.taskmigo.console.access.internal.domain.oauth.client.RegisteredClientState;
import com.taskmigo.console.access.internal.domain.oauth.client.StoredClient;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ActiveRegisteredClientRepository implements RegisteredClientRepository {
  private final JdbcRegisteredClientRepository clients;
  private final RegisteredClientStateRepository states;
  private final RegisteredClientRecordRepository records;
  private final Clock clock;

  public ActiveRegisteredClientRepository(
      JdbcOperations jdbcOperations,
      RegisteredClientStateRepository states,
      RegisteredClientRecordRepository records,
      Clock clock) {
    this.clients = new JdbcRegisteredClientRepository(jdbcOperations);
    this.states = states;
    this.records = records;
    this.clock = clock;
  }

  @Override
  @Transactional
  public void save(RegisteredClient client) {
    this.clients.save(client);
    if (!this.states.existsById(client.getId())) {
      this.states.save(new RegisteredClientState(client.getId(), this.clock.instant()));
    }
  }

  @Transactional
  public void saveFromConfiguration(RegisteredClient client) {
    this.clients.save(client);
    RegisteredClientState state = this.stateFor(client.getId());
    state.activateFromConfiguration(this.clock.instant());
    this.states.save(state);
  }

  @Override
  @Transactional(readOnly = true)
  public RegisteredClient findById(String id) {
    return this.isActive(id) ? this.clients.findById(id) : null;
  }

  @Override
  @Transactional(readOnly = true)
  public RegisteredClient findByClientId(String clientId) {
    RegisteredClient client = this.clients.findByClientId(clientId);
    return client != null && this.isActive(client.getId()) ? client : null;
  }

  @Transactional(readOnly = true)
  public RegisteredClient findIncludingInactiveByClientId(String clientId) {
    return this.clients.findByClientId(clientId);
  }

  @Transactional(readOnly = true)
  public RegisteredClient findIncludingInactiveById(String id) {
    return this.clients.findById(id);
  }

  @Transactional(readOnly = true)
  public List<StoredClient> findAllIncludingInactive() {
    return this.states.findAll().stream()
        .map(
            state ->
                ActiveRegisteredClientRepository.toStoredClient(
                    state, this.clients.findById(state.getRegisteredClientId())))
        .filter(Objects::nonNull)
        .toList();
  }

  @Transactional
  public void disableClientsNotIn(Set<String> configuredRegisteredClientIds) {
    Instant now = this.clock.instant();
    this.states
        .findAll()
        .forEach(
            state -> {
              if (!configuredRegisteredClientIds.contains(state.getRegisteredClientId())
                  && state.disableIfNotManuallyEnabled(now)) {
                this.states.save(state);
              }
            });
  }

  @Transactional
  public StoredClient enableManually(String clientId) {
    RegisteredClient client = this.clients.findByClientId(clientId);
    if (client == null) {
      return null;
    }
    RegisteredClientState state = this.stateFor(client.getId());
    state.enableManually(this.clock.instant());
    this.states.save(state);
    return ActiveRegisteredClientRepository.toStoredClient(state, client);
  }

  @Transactional
  public void deleteByRegisteredClientId(String registeredClientId) {
    this.records.deleteById(registeredClientId);
    this.records.flush();
  }

  private boolean isActive(String registeredClientId) {
    return this.states
        .findById(registeredClientId)
        .map(RegisteredClientState::isActive)
        .orElse(false);
  }

  private RegisteredClientState stateFor(String registeredClientId) {
    return this.states
        .findById(registeredClientId)
        .orElseGet(() -> new RegisteredClientState(registeredClientId, this.clock.instant()));
  }

  private static StoredClient toStoredClient(RegisteredClientState state, RegisteredClient client) {
    return client == null
        ? null
        : new StoredClient(
            client, state.isActive(), state.isManualOverride(), state.getUpdatedAt());
  }
}

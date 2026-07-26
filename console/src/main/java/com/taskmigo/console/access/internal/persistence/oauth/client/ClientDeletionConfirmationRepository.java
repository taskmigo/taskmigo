package com.taskmigo.console.access.internal.persistence.oauth.client;

import com.taskmigo.console.access.internal.domain.oauth.client.ClientDeletionConfirmation;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientDeletionConfirmationRepository
    extends JpaRepository<ClientDeletionConfirmation, UUID> {
  public Optional<ClientDeletionConfirmation> findByTokenHash(String var1);

  public void deleteAllByRegisteredClientId(String var1);

  public void deleteAllByExpiresAtBefore(Instant var1);
}

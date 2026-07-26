package com.taskmigo.console.access.internal.persistence.signing;

import com.taskmigo.console.access.internal.domain.signing.SigningKey;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SigningKeyRepository extends JpaRepository<SigningKey, String> {
  public List<SigningKey> findAllByActiveTrue();

  public boolean existsByActiveTrue();
}

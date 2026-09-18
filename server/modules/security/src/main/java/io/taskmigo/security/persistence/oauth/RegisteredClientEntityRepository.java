package io.taskmigo.security.persistence.oauth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface RegisteredClientEntityRepository extends JpaRepository<RegisteredClientEntity, String> {
    Optional<RegisteredClientEntity> findByClientId(String clientId);
}

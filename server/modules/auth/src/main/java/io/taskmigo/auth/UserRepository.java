package io.taskmigo.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface UserRepository extends JpaRepository<UserEntity, UUID>, JpaSpecificationExecutor<UserEntity> {
    Optional<UserEntity> findByUsername(String username);
}

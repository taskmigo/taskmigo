package io.taskmigo.authorization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationUserRepository extends JpaRepository<AuthorizationUserEntity, UUID> {}

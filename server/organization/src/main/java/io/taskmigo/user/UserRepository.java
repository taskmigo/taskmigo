package io.taskmigo.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRepository extends JpaRepository<UserEntity, UUID> {}

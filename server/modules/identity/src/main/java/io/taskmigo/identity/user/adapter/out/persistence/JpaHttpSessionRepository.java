package io.taskmigo.identity.user.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/// Removes every session indexed by the immutable User id in the lifecycle transaction.
interface JpaHttpSessionRepository extends JpaRepository<HttpSessionEntity, String> {
    long deleteByPrincipalName(String principalName);
}

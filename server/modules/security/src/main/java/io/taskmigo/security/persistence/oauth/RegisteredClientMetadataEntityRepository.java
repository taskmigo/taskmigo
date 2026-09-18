package io.taskmigo.security.persistence.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

interface RegisteredClientMetadataEntityRepository extends JpaRepository<RegisteredClientMetadataEntity, String> {}

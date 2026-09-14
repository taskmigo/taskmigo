package io.taskmigo.identity.persistence.oauth;

import org.springframework.data.jpa.repository.JpaRepository;

interface RegisteredClientMetadataEntityRepository extends JpaRepository<RegisteredClientMetadataEntity, String> {}

package io.taskmigo.identity.configuration.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaConfigurationRepository extends JpaRepository<ConfigurationEntity, String> {}

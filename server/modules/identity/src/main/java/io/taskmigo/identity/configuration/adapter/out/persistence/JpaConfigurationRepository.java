package io.taskmigo.identity.configuration.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface JpaConfigurationRepository extends JpaRepository<ConfigurationEntity, String> {}

package com.taskmigo.console.access.internal.persistence.oauth.client;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisteredClientRecordRepository
    extends JpaRepository<RegisteredClientRecord, String> {}

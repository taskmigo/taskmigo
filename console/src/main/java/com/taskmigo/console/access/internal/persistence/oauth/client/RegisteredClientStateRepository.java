package com.taskmigo.console.access.internal.persistence.oauth.client;

import com.taskmigo.console.access.internal.domain.oauth.client.RegisteredClientState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisteredClientStateRepository
    extends JpaRepository<RegisteredClientState, String> {}

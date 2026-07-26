package com.taskmigo.console.access.internal.persistence.identity;

import com.taskmigo.console.access.internal.domain.identity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {}

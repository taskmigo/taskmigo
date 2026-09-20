package io.taskmigo.architecture.fixtures.application.service;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ApplicationService {

    EntityManager entityManager();
}

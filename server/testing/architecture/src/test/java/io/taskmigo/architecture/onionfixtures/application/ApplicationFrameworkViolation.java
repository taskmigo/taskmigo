package io.taskmigo.architecture.onionfixtures.application;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ApplicationFrameworkViolation {
    EntityManager entityManager();
}

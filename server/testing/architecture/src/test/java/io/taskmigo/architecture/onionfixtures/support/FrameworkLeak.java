package io.taskmigo.architecture.onionfixtures.support;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface FrameworkLeak {
    EntityManager entityManager();
}

package io.taskmigo.architecture.onionfixtures.persistence;

import jakarta.persistence.EntityManager;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface PersistenceComponent {
    EntityManager entityManager();
}

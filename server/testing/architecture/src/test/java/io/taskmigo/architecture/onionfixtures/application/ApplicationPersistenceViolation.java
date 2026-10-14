package io.taskmigo.architecture.onionfixtures.application;

import io.taskmigo.architecture.onionfixtures.persistence.PersistenceComponent;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ApplicationPersistenceViolation {
    PersistenceComponent persistence();
}

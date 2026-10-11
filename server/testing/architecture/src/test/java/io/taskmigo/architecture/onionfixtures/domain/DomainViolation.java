package io.taskmigo.architecture.onionfixtures.domain;

import io.taskmigo.architecture.onionfixtures.application.ApplicationService;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface DomainViolation {
    ApplicationService service();
}

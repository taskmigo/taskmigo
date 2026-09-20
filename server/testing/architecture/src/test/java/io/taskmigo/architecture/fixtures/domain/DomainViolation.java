package io.taskmigo.architecture.fixtures.domain;

import io.taskmigo.architecture.fixtures.application.service.ApplicationService;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface DomainViolation {
    ApplicationService service();
}

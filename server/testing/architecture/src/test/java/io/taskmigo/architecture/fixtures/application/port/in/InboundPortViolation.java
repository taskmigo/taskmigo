package io.taskmigo.architecture.fixtures.application.port.in;

import io.taskmigo.architecture.fixtures.application.service.ApplicationService;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface InboundPortViolation {
    ApplicationService implementation();
}

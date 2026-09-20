package io.taskmigo.architecture.fixtures.application.port.out;

import io.taskmigo.architecture.fixtures.adapter.out.persistence.DrivenAdapterViolation;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface OutboundPortViolation {
    DrivenAdapterViolation adapter();
}

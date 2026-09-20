package io.taskmigo.architecture.fixtures.adapter.out.persistence;

import io.taskmigo.architecture.fixtures.application.service.ApplicationService;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface DrivenAdapterViolation {

    ApplicationService service();
}

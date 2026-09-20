package io.taskmigo.architecture.fixtures.adapter.in.http;

import io.taskmigo.architecture.fixtures.application.service.ApplicationService;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface DrivingAdapterViolation {

    ApplicationService implementation();
}

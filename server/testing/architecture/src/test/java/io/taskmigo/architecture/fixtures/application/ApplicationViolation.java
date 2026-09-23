package io.taskmigo.architecture.fixtures.application;

import io.taskmigo.architecture.fixtures.adapter.out.persistence.DrivenAdapterViolation;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface ApplicationViolation {
    DrivenAdapterViolation adapter();
}

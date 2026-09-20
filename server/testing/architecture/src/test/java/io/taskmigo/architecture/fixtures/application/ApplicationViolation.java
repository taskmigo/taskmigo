package io.taskmigo.architecture.fixtures.application;

import io.taskmigo.architecture.fixtures.legacy.LegacyPersistenceAdapter;
import org.jspecify.annotations.NullMarked;

@NullMarked
interface ApplicationViolation {
    LegacyPersistenceAdapter adapter();
}

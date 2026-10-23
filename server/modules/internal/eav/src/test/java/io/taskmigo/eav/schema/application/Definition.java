package io.taskmigo.eav.schema.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

record Definition(UUID id, String key, String name, @Nullable UUID currentVersionId) {}

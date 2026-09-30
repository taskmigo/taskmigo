package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.model.AuditChange;
import java.util.Set;
import org.jspecify.annotations.Nullable;

final class UserAuditChanges {

    private static final Set<String> SENSITIVE_FIELDS = Set.of("passwordHash");

    private UserAuditChanges() {}

    static AuditChange visible(String field, @Nullable Object before, @Nullable Object after) {
        return SENSITIVE_FIELDS.contains(field)
            ? AuditChange.sensitive(field)
            : AuditChange.visible(field, before, after);
    }
}

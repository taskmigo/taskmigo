package io.taskmigo.identity.user.application;

import io.taskmigo.audit.application.record.AuditRecorder;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.application.internal.UserTombstoneService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;

/// Permanently removes User PII/access data while preserving the stable tombstone row and audit history.
public final class DefaultUserTombstoneService implements UserTombstoneService {

    private static final String ENTITY_TYPE = "user";

    private final UserCommandService users;
    private final UserAccessRevocationService access;
    private final UserAuditScrubber auditScrubber;
    private final AuditRecorder audits;

    public DefaultUserTombstoneService(
        UserCommandService users,
        UserAccessRevocationService access,
        UserAuditScrubber auditScrubber,
        AuditRecorder audits
    ) {
        this.users = users;
        this.access = access;
        this.auditScrubber = auditScrubber;
        this.audits = audits;
    }

    @Override
    public void tombstone(User target, Instant tombstonedAt) {
        String beforeStatus = target.status().name();
        this.access.revoke(target);
        target.tombstone(tombstonedAt);
        this.users.save(target);

        List<AuditChange> changes = List.of(
            AuditChange.sensitive("username"),
            AuditChange.sensitive("firstName"),
            AuditChange.sensitive("lastName"),
            AuditChange.sensitive("emails"),
            AuditChange.sensitive("passwordHash"),
            AuditChange.sensitive("roleIds"),
            AuditChange.sensitive("statementIds"),
            AuditChange.sensitive("groupIds"),
            AuditChange.sensitive("sessions"),
            AuditChange.visible("status", beforeStatus, "TOMBSTONE"),
            AuditChange.visible("tombstonedAt", null, tombstonedAt)
        );

        this.audits.record(ENTITY_TYPE, target.id(), AuditOperation.DELETE, () -> changes);
        this.auditScrubber.scrub(target.id());
    }
}

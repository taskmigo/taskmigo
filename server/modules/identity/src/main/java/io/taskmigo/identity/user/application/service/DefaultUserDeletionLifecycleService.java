package io.taskmigo.identity.user.application.service;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserDeletionLifecycleService;
import io.taskmigo.identity.user.application.port.in.internal.UserTombstoneService;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// Applies the configured delete-to-retention or delete-to-tombstone lifecycle.
public final class DefaultUserDeletionLifecycleService implements UserDeletionLifecycleService {

    private static final String ENTITY_TYPE = "user";

    private final ConfigurationService configuration;
    private final UserAccessRevocationService access;
    private final UserTombstoneService tombstones;
    private final UserCommandService users;
    private final UserAuditAppender audits;

    public DefaultUserDeletionLifecycleService(
        ConfigurationService configuration,
        UserAccessRevocationService access,
        UserTombstoneService tombstones,
        UserCommandService users,
        UserAuditAppender audits
    ) {
        this.configuration = configuration;
        this.access = access;
        this.tombstones = tombstones;
        this.users = users;
        this.audits = audits;
    }

    @Override
    public void delete(User target, UserMutationActor actor, Instant occurredAt) {
        target.requireMutable();
        target.requireManagedDeletionAllowed();

        if (this.configuration.get().retention().user().immediate()) {
            this.tombstones.tombstone(target, actor, occurredAt);
            return;
        }

        String beforeStatus = target.status().name();
        UserAccessRevocation revoked = this.access.revoke(target);
        target.retain(occurredAt);
        this.users.save(target);

        List<AuditChange> changes = new ArrayList<>();
        if (revoked.roles()) {
            changes.add(AuditChange.sensitive("roleIds"));
        }
        if (revoked.statements()) {
            changes.add(AuditChange.sensitive("statementIds"));
        }
        if (revoked.groups()) {
            changes.add(AuditChange.sensitive("groupIds"));
        }
        if (revoked.sessions()) {
            changes.add(AuditChange.sensitive("sessions"));
        }
        changes.add(AuditChange.visible("status", beforeStatus, "RETAINED"));
        changes.add(AuditChange.visible("retainedAt", null, occurredAt));

        this.audits.append(
            new AuditEvent(
                UUID.randomUUID(),
                ENTITY_TYPE,
                target.id(),
                new AuditActor(actor.id(), actor.username()),
                occurredAt,
                List.copyOf(changes)
            )
        );
    }
}

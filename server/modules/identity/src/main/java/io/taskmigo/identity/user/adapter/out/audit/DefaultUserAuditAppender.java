package io.taskmigo.identity.user.adapter.out.audit;

import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import org.springframework.stereotype.Component;

/// Adapts User audit writes to the synchronous Audit application boundary.
@Component
final class DefaultUserAuditAppender implements UserAuditAppender {

    private final AuditAppendService audits;

    DefaultUserAuditAppender(AuditAppendService audits) {
        this.audits = audits;
    }

    @Override
    public void append(AuditEvent event) {
        this.audits.append(event);
    }
}

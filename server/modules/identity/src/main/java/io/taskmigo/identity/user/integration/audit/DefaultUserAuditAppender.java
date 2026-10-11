package io.taskmigo.identity.user.integration.audit;

import io.taskmigo.audit.application.append.AuditAppendService;
import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.identity.user.application.UserAuditAppender;
import org.springframework.stereotype.Component;

/// Integrates User audit writes with the synchronous Audit application boundary.
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

package io.taskmigo.identity.user.adapter.out.audit;

import io.taskmigo.audit.application.port.in.privacy.AuditPrivacyService;
import io.taskmigo.identity.user.application.port.out.UserAuditScrubber;
import java.util.UUID;
import org.springframework.stereotype.Component;

/// Adapts User tombstone privacy cleanup to the Audit application boundary.
@Component
final class DefaultUserAuditScrubber implements UserAuditScrubber {

    private final AuditPrivacyService audits;

    DefaultUserAuditScrubber(AuditPrivacyService audits) {
        this.audits = audits;
    }

    @Override
    public void scrub(UUID userId) {
        this.audits.scrubUser(userId);
    }
}

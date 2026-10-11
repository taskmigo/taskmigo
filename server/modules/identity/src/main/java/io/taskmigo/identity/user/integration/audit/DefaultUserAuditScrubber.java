package io.taskmigo.identity.user.integration.audit;

import io.taskmigo.audit.application.port.in.privacy.AuditPrivacyService;
import io.taskmigo.identity.user.application.UserAuditScrubber;
import java.util.UUID;
import org.springframework.stereotype.Component;

/// Integrates User tombstone privacy cleanup with the Audit application boundary.
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

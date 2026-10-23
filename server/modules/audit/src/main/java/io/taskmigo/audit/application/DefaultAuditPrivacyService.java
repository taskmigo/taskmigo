package io.taskmigo.audit.application;

import io.taskmigo.audit.application.privacy.AuditPrivacyService;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Performs privacy scrubbing inside the caller-owned business transaction.
public class DefaultAuditPrivacyService implements AuditPrivacyService {

    private final AuditLogStore logs;

    public DefaultAuditPrivacyService(AuditLogStore logs) {
        this.logs = logs;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public void scrubUser(UUID userId) {
        this.logs.scrubUser(userId);
    }
}

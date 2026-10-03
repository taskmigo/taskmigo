package io.taskmigo.audit.application.port.in.privacy;

import java.util.UUID;

/// Scrubs purgeable User PII from historical audit records without deleting history.
public interface AuditPrivacyService {
    void scrubUser(UUID userId);
}

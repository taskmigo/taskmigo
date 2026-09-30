package io.taskmigo.audit.adapter.out.jobrunr;

import io.taskmigo.audit.application.port.in.append.AuditAppendService;
import io.taskmigo.audit.event.AuditEvent;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;

/// Bridges Spring Modulith's durable JobRunr externalization to the audit append use case.
public final class AuditJobRunrTransport implements JobRunrExternalizationTransport {

    private final AuditAppendService audits;

    public AuditJobRunrTransport(AuditAppendService audits) {
        this.audits = audits;
    }

    /// Appends one externalized audit event; JobRunr retries failures and may invoke this more than once.
    @Override
    @Job(name = "Append durable audit event", retries = 10, labels = { "audit" })
    public void externalize(Object event) {
        if (!(event instanceof AuditEvent auditEvent)) {
            throw new IllegalArgumentException("Unsupported audit event payload: " + event.getClass().getName());
        }
        this.audits.append(auditEvent);
    }
}

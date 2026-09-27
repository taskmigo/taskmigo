package io.taskmigo.audit.adapter.in.jobrunr;

import io.taskmigo.audit.application.port.in.internal.AuditAppendService;
import io.taskmigo.audit.model.AuditMutationEvent;
import org.springframework.modulith.events.jobrunr.JobRunrExternalizationTransport;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/// Executes durable audit jobs inside the worker and acknowledges them only after the audit transaction commits.
@Component
class JobRunrAuditTransport implements JobRunrExternalizationTransport {

    private final AuditAppendService appender;

    JobRunrAuditTransport(AuditAppendService appender) {
        this.appender = appender;
    }

    @Override
    @Transactional
    public void externalize(Object event) {
        this.appender.append(AuditMutationEvent.class.cast(event));
    }
}

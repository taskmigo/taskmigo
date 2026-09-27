package io.taskmigo.audit.adapter.in.outbox;

import io.namastack.outbox.annotation.OutboxHandler;
import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.application.service.AuditLogAppender;
import org.springframework.stereotype.Component;

/// Appends final audit rows when the worker processes durable outbox records.
@Component
final class AuditOutboxHandler {

    static final String HANDLER_ID = "taskmigo.audit.append";

    private final AuditLogAppender appender;

    AuditOutboxHandler(AuditLogAppender appender) {
        this.appender = appender;
    }

    @OutboxHandler(id = HANDLER_ID)
    public void append(AuditEvent event) {
        this.appender.append(event);
    }
}

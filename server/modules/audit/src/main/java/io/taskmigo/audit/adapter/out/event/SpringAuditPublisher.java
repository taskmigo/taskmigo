package io.taskmigo.audit.adapter.out.event;

import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/// Adapts the audit publication contract to Spring Modulith application events.
@Component
final class SpringAuditPublisher implements AuditPublisher {

    private final ApplicationEventPublisher events;

    SpringAuditPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void publish(AuditEvent event) {
        this.events.publishEvent(event);
    }
}

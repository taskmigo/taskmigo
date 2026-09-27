package io.taskmigo.audit.adapter.out.event;

import io.taskmigo.audit.application.port.out.AuditEventPublisher;
import io.taskmigo.audit.model.AuditMutationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
final class SpringAuditEventPublisher implements AuditEventPublisher {

    private final ApplicationEventPublisher events;

    SpringAuditEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void publish(AuditMutationEvent event) {
        this.events.publishEvent(event);
    }
}

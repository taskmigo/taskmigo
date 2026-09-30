package io.taskmigo.identity.user.adapter.out.audit;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.identity.user.application.port.out.UserAuditPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/// Adapts User audit publication to Spring application events for Spring Modulith externalization.
@Component
final class SpringUserAuditPublisher implements UserAuditPublisher {

    private final ApplicationEventPublisher events;

    SpringUserAuditPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void publish(AuditEvent event) {
        this.events.publishEvent(event);
    }
}

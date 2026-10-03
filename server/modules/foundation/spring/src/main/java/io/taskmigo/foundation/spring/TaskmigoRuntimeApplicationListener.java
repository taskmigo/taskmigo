package io.taskmigo.foundation.spring;

import io.taskmigo.foundation.TaskmigoRuntime;
import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/// Enforces Taskmigo's process-wide runtime invariants at the earliest Spring Boot application event.
///
/// The listener is loaded declaratively for every Taskmigo Spring application, including test bootstraps that do not
/// invoke an executable application's main method.
public final class TaskmigoRuntimeApplicationListener
    implements ApplicationListener<ApplicationStartingEvent>, Ordered
{

    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
        TaskmigoRuntime.initialize();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

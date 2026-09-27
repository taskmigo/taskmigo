package io.taskmigo.audit.adapter.out.outbox;

import io.micrometer.observation.ObservationRegistry;
import io.namastack.outbox.OutboxProperties;
import io.namastack.outbox.instance.OutboxInstance;
import io.namastack.outbox.instance.OutboxInstanceRegistry;
import io.namastack.outbox.instance.OutboxInstanceRepository;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

/// Keeps producer-only applications out of Namastack partition ownership while retaining transactional scheduling.
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "taskmigo.audit.outbox.producer-only", havingValue = "true")
class AuditOutboxProducerConfiguration {

    @Bean
    OutboxInstanceRegistry producerOnlyOutboxInstanceRegistry(
        OutboxInstanceRepository instances,
        OutboxProperties properties,
        Clock clock,
        @Qualifier(OutboxInstanceRegistry.SCHEDULER_NAME) TaskScheduler scheduler,
        ObjectProvider<ObservationRegistry> observations
    ) {
        return new OutboxInstanceRegistry(
            instances,
            properties,
            clock,
            scheduler,
            () -> observations.getIfAvailable(() -> ObservationRegistry.NOOP),
            "producer-only-" + UUID.randomUUID()
        ) {
            @Override
            public void start() {}

            @Override
            public void stop() {}

            @Override
            public boolean isRunning() {
                return false;
            }

            @Override
            public List<OutboxInstance> getActiveInstances() {
                return List.of();
            }

            @Override
            public Set<String> getActiveInstanceIds() {
                return Set.of();
            }

            @Override
            public boolean isInstanceActive(String instanceId) {
                return false;
            }

            @Override
            public long getActiveInstanceCount() {
                return 0;
            }
        };
    }
}

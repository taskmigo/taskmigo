package io.taskmigo.worker;

import io.taskmigo.project.ProjectService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
final class ProjectRetentionJob {

    private final ProjectService projects;
    private final Duration retention;

    ProjectRetentionJob(ProjectService projects, @Value("${taskmigo.project.retention-days:30}") long retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("Project retention days must be at least 1");
        this.projects = projects;
        this.retention = Duration.ofDays(retentionDays);
    }

    @Scheduled(fixedDelayString = "${taskmigo.project.retention-cleanup-interval:PT1H}")
    void deleteExpiredArchivedProjects() {
        this.projects.deleteArchivedBefore(Instant.now().minus(this.retention));
    }
}

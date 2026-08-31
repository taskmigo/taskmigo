package io.taskmigo.web.testing;

import io.taskmigo.access.AccessTestDataCleaner;
import io.taskmigo.group.GroupTestDataCleaner;
import io.taskmigo.history.ProjectHistoryTestDataCleaner;
import io.taskmigo.organization.OrganizationTestDataCleaner;
import io.taskmigo.project.ProjectTestDataCleaner;
import io.taskmigo.user.UserTestDataCleaner;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "taskmigo.testing", name = "cleanup-enabled", havingValue = "true")
class TestDataCleanupService {

    private final ProjectHistoryTestDataCleaner history;
    private final ProjectTestDataCleaner projects;
    private final AccessTestDataCleaner access;
    private final GroupTestDataCleaner groups;
    private final UserTestDataCleaner users;
    private final OrganizationTestDataCleaner organizations;

    TestDataCleanupService(
        ProjectHistoryTestDataCleaner history,
        ProjectTestDataCleaner projects,
        AccessTestDataCleaner access,
        GroupTestDataCleaner groups,
        UserTestDataCleaner users,
        OrganizationTestDataCleaner organizations
    ) {
        this.history = history;
        this.projects = projects;
        this.access = access;
        this.groups = groups;
        this.users = users;
        this.organizations = organizations;
    }

    @Transactional
    void cleanup(Resources resources) {
        this.history.purgeProjects(resources.projects());
        this.projects.purge(resources.projects());
        this.access.purge(resources.roles());
        this.groups.purge(resources.groups());
        this.users.purge(resources.users());
        this.organizations.purge(resources.organizations());
    }

    record Resources(
        Set<UUID> organizations,
        Set<UUID> users,
        Set<UUID> roles,
        Set<UUID> groups,
        Set<UUID> projects
    ) {}
}

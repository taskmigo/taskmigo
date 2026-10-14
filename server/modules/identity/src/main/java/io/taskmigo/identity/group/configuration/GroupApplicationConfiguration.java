package io.taskmigo.identity.group.configuration;

import io.taskmigo.authorization.subject.application.api.SubjectGrantAssignmentService;
import io.taskmigo.identity.application.TransactionRunner;
import io.taskmigo.identity.group.application.DefaultGroupCommandService;
import io.taskmigo.identity.group.application.DefaultGroupService;
import io.taskmigo.identity.group.application.GroupCommandRepository;
import io.taskmigo.identity.group.application.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.GroupQueryRepository;
import io.taskmigo.identity.group.application.api.GroupService;
import io.taskmigo.identity.group.application.internal.GroupCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class GroupApplicationConfiguration {

    @Bean
    GroupCommandService defaultGroupCommandService(GroupCommandRepository groups) {
        return new DefaultGroupCommandService(groups);
    }

    @Bean
    GroupService defaultGroupService(
        GroupCommandService commands,
        GroupQueryRepository groups,
        GroupHierarchyRepository hierarchies,
        SubjectGrantAssignmentService grantAssignments,
        TransactionRunner transactions
    ) {
        return new DefaultGroupService(commands, groups, hierarchies, grantAssignments, transactions);
    }
}

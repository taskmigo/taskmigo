package io.taskmigo.identity.group.composition;

import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.group.application.port.in.internal.GroupCommandService;
import io.taskmigo.identity.group.application.port.out.GroupCommandRepository;
import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.port.out.GroupQueryRepository;
import io.taskmigo.identity.group.application.service.DefaultGroupCommandService;
import io.taskmigo.identity.group.application.service.DefaultGroupService;
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

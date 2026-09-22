package io.taskmigo.identity.provisioning.composition;

import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.group.application.port.in.internal.GroupCommandService;
import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.identity.provisioning.application.service.DefaultGroupProvisioningService;
import io.taskmigo.identity.provisioning.application.service.DefaultIdentityProvisioningService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProvisioningApplicationConfiguration {

    @Bean
    GroupProvisioningService defaultGroupProvisioningService(
        GroupCommandService groups,
        GroupHierarchyRepository hierarchies,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        TransactionRunner transactions
    ) {
        return new DefaultGroupProvisioningService(groups, hierarchies, grantAssignments, grantQueries, transactions);
    }

    @Bean
    IdentityProvisioningService defaultIdentityProvisioningService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        MembershipService memberships,
        TransactionRunner transactions
    ) {
        return new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, memberships, transactions);
    }
}

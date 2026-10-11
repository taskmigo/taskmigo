package io.taskmigo.identity.provisioning.configuration;

import io.taskmigo.authorization.subject.application.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.TransactionRunner;
import io.taskmigo.identity.group.application.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.internal.GroupCommandService;
import io.taskmigo.identity.membership.application.api.MembershipService;
import io.taskmigo.identity.provisioning.application.DefaultGroupProvisioningService;
import io.taskmigo.identity.provisioning.application.DefaultIdentityProvisioningService;
import io.taskmigo.identity.provisioning.application.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.api.IdentityProvisioningService;
import io.taskmigo.identity.user.application.UserAuditAppender;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.application.internal.UserDeletionLifecycleService;
import java.time.Clock;
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
        UserDeletionLifecycleService deletion,
        UserAuditAppender audits,
        TransactionRunner transactions
    ) {
        return new DefaultIdentityProvisioningService(
            users,
            grantAssignments,
            grantQueries,
            memberships,
            deletion,
            audits,
            transactions,
            Clock.systemUTC()
        );
    }
}

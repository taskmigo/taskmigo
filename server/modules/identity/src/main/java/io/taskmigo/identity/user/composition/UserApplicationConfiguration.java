package io.taskmigo.identity.user.composition;

import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.user.application.port.in.api.UserRegistrationService;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.identity.user.application.service.DefaultUserCommandService;
import io.taskmigo.identity.user.application.service.DefaultUserRetentionService;
import io.taskmigo.identity.user.application.service.DefaultUserService;
import io.taskmigo.identity.user.application.service.UserRegistrationApplicationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class UserApplicationConfiguration {

    @Bean
    UserCommandService defaultUserCommandService(UserCommandRepository users) {
        return new DefaultUserCommandService(users);
    }

    @Bean
    UserService defaultUserService(
        UserQueryRepository users,
        UserCommandService commands,
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        MembershipService memberships,
        ConfigurationService configuration,
        UserAuditAppender audits,
        TransactionRunner transactions
    ) {
        return new DefaultUserService(
            users,
            commands,
            grantQueries,
            grantAssignments,
            memberships,
            configuration,
            audits,
            transactions,
            Clock.systemUTC()
        );
    }

    @Bean
    UserRetentionService defaultUserRetentionService(
        UserCommandService users,
        ConfigurationService configuration,
        TransactionRunner transactions
    ) {
        return new DefaultUserRetentionService(users, configuration, transactions);
    }

    @Bean
    UserRegistrationService userRegistrationApplicationService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        GroupService groups,
        MembershipService memberships,
        TransactionRunner transactions
    ) {
        return new UserRegistrationApplicationService(users, grantAssignments, groups, memberships, transactions);
    }
}

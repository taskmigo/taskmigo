package io.taskmigo.identity.user.composition;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.user.application.port.in.api.UserRegistrationService;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.identity.user.application.service.DefaultUserCommandService;
import io.taskmigo.identity.user.application.service.DefaultUserService;
import io.taskmigo.identity.user.application.service.UserRegistrationApplicationService;
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
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        TransactionRunner transactions
    ) {
        return new DefaultUserService(users, grantQueries, grantAssignments, transactions);
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

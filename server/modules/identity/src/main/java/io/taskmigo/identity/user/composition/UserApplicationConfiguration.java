package io.taskmigo.identity.user.composition;

import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.membership.application.port.in.internal.MembershipCleanupService;
import io.taskmigo.identity.user.application.port.in.api.UserRegistrationService;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.identity.user.application.port.in.api.UserSessionLifecycleService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserDeletionLifecycleService;
import io.taskmigo.identity.user.application.port.in.internal.UserTombstoneService;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.application.port.out.UserAuditScrubber;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.identity.user.application.port.out.UserSessionStore;
import io.taskmigo.identity.user.application.service.DefaultUserCommandService;
import io.taskmigo.identity.user.application.service.DefaultUserDeletionLifecycleService;
import io.taskmigo.identity.user.application.service.DefaultUserRetentionService;
import io.taskmigo.identity.user.application.service.DefaultUserService;
import io.taskmigo.identity.user.application.service.DefaultUserSessionLifecycleService;
import io.taskmigo.identity.user.application.service.DefaultUserTombstoneService;
import io.taskmigo.identity.user.application.service.UserAccessRevocationService;
import io.taskmigo.identity.user.application.service.UserRegistrationApplicationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class UserApplicationConfiguration {

    @Bean
    UserAccessRevocationService userAccessRevocationService(
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        MembershipCleanupService memberships,
        UserSessionStore sessions
    ) {
        return new UserAccessRevocationService(grantQueries, grantAssignments, memberships, sessions);
    }

    @Bean
    UserDeletionLifecycleService defaultUserDeletionLifecycleService(
        ConfigurationService configuration,
        UserAccessRevocationService access,
        UserTombstoneService tombstones,
        UserCommandService users,
        UserAuditAppender audits
    ) {
        return new DefaultUserDeletionLifecycleService(configuration, access, tombstones, users, audits);
    }

    @Bean
    UserTombstoneService defaultUserTombstoneService(
        UserCommandService users,
        UserAccessRevocationService access,
        UserAuditScrubber auditScrubber,
        UserAuditAppender audits
    ) {
        return new DefaultUserTombstoneService(users, access, auditScrubber, audits);
    }

    @Bean
    UserCommandService defaultUserCommandService(UserCommandRepository users) {
        return new DefaultUserCommandService(users);
    }

    @Bean
    UserSessionLifecycleService userSessionLifecycleService(UserCommandService users, TransactionRunner transactions) {
        return new DefaultUserSessionLifecycleService(users, transactions);
    }

    @Bean
    UserService defaultUserService(
        UserQueryRepository users,
        UserCommandService commands,
        SubjectGrantQueryService grantQueries,
        SubjectGrantAssignmentService grantAssignments,
        UserDeletionLifecycleService deletion,
        UserAuditAppender audits,
        TransactionRunner transactions
    ) {
        return new DefaultUserService(
            users,
            commands,
            grantQueries,
            grantAssignments,
            deletion,
            audits,
            transactions,
            Clock.systemUTC()
        );
    }

    @Bean
    UserRetentionService defaultUserRetentionService(
        UserCommandService users,
        ConfigurationService configuration,
        UserTombstoneService tombstones,
        TransactionRunner transactions
    ) {
        return new DefaultUserRetentionService(users, configuration, tombstones, transactions);
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

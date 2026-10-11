package io.taskmigo.identity.user.configuration;

import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.TransactionRunner;
import io.taskmigo.identity.configuration.application.api.ConfigurationService;
import io.taskmigo.identity.group.application.api.GroupService;
import io.taskmigo.identity.membership.application.api.MembershipService;
import io.taskmigo.identity.membership.application.internal.MembershipCleanupService;
import io.taskmigo.identity.user.application.DefaultUserCommandService;
import io.taskmigo.identity.user.application.DefaultUserDeletionLifecycleService;
import io.taskmigo.identity.user.application.DefaultUserRetentionService;
import io.taskmigo.identity.user.application.DefaultUserService;
import io.taskmigo.identity.user.application.DefaultUserSessionLifecycleService;
import io.taskmigo.identity.user.application.DefaultUserTombstoneService;
import io.taskmigo.identity.user.application.UserAccessRevocationService;
import io.taskmigo.identity.user.application.UserAuditAppender;
import io.taskmigo.identity.user.application.UserAuditScrubber;
import io.taskmigo.identity.user.application.UserCommandRepository;
import io.taskmigo.identity.user.application.UserQueryRepository;
import io.taskmigo.identity.user.application.UserRegistrationApplicationService;
import io.taskmigo.identity.user.application.UserSessionStore;
import io.taskmigo.identity.user.application.api.UserRegistrationService;
import io.taskmigo.identity.user.application.api.UserRetentionService;
import io.taskmigo.identity.user.application.api.UserService;
import io.taskmigo.identity.user.application.api.UserSessionLifecycleService;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.application.internal.UserDeletionLifecycleService;
import io.taskmigo.identity.user.application.internal.UserTombstoneService;
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

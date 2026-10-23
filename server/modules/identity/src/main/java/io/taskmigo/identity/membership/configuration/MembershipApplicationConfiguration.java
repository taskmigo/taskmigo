package io.taskmigo.identity.membership.configuration;

import io.taskmigo.identity.group.application.api.GroupService;
import io.taskmigo.identity.membership.application.DefaultMembershipCleanupService;
import io.taskmigo.identity.membership.application.DefaultMembershipService;
import io.taskmigo.identity.membership.application.MembershipRepository;
import io.taskmigo.identity.membership.application.api.MembershipService;
import io.taskmigo.identity.membership.application.internal.MembershipCleanupService;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MembershipApplicationConfiguration {

    @Bean
    MembershipCleanupService defaultMembershipCleanupService(MembershipRepository memberships) {
        return new DefaultMembershipCleanupService(memberships);
    }

    @Bean
    MembershipService defaultMembershipService(
        MembershipRepository memberships,
        GroupService groups,
        UserCommandService users
    ) {
        return new DefaultMembershipService(memberships, groups, users);
    }
}

package io.taskmigo.identity.membership.composition;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.membership.application.port.out.MembershipRepository;
import io.taskmigo.identity.membership.application.service.DefaultMembershipService;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MembershipApplicationConfiguration {

    @Bean
    MembershipService defaultMembershipService(
        MembershipRepository memberships,
        GroupService groups,
        UserService users,
        TransactionRunner transactions
    ) {
        return new DefaultMembershipService(memberships, groups, users, transactions);
    }
}

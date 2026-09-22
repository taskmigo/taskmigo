package io.taskmigo.authorization.subject.composition;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectRoleQueryService;
import io.taskmigo.authorization.subject.application.port.out.SubjectGrantRepository;
import io.taskmigo.authorization.subject.application.port.out.resolution.EffectiveSubjectResolver;
import io.taskmigo.authorization.subject.application.service.DefaultSubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.service.DefaultSubjectGrantQueryService;
import io.taskmigo.authorization.subject.application.service.DefaultSubjectRoleQueryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SubjectApplicationConfiguration {

    @Bean
    SubjectGrantAssignmentService defaultSubjectGrantAssignmentService(
        RoleService roles,
        StatementService statements,
        SubjectGrantRepository grants,
        TransactionRunner transactions
    ) {
        return new DefaultSubjectGrantAssignmentService(roles, statements, grants, transactions);
    }

    @Bean
    SubjectGrantQueryService defaultSubjectGrantQueryService(
        SubjectGrantRepository grants,
        TransactionRunner transactions
    ) {
        return new DefaultSubjectGrantQueryService(grants, transactions);
    }

    @Bean
    SubjectRoleQueryService defaultSubjectRoleQueryService(
        EffectiveSubjectResolver subjects,
        SubjectGrantRepository grants,
        RoleService roles,
        TransactionRunner transactions
    ) {
        return new DefaultSubjectRoleQueryService(subjects, grants, roles, transactions);
    }
}

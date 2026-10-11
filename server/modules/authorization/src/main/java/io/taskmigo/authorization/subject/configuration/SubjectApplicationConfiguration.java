package io.taskmigo.authorization.subject.configuration;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.application.api.RoleService;
import io.taskmigo.authorization.statement.application.api.StatementService;
import io.taskmigo.authorization.subject.application.DefaultSubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.DefaultSubjectGrantQueryService;
import io.taskmigo.authorization.subject.application.DefaultSubjectRoleQueryService;
import io.taskmigo.authorization.subject.application.SubjectGrantRepository;
import io.taskmigo.authorization.subject.application.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.api.SubjectGrantQueryService;
import io.taskmigo.authorization.subject.application.api.SubjectRoleQueryService;
import io.taskmigo.authorization.subject.application.resolution.EffectiveSubjectResolver;
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

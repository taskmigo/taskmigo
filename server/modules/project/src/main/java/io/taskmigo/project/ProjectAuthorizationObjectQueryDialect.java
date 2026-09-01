package io.taskmigo.project;

import io.taskmigo.authorization.AuthorizationExpression;
import io.taskmigo.authorization.AuthorizationObjectQueryDialect;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
final class ProjectAuthorizationObjectQueryDialect implements AuthorizationObjectQueryDialect {

    @Override
    public String method() {
        return "GET";
    }

    @Override
    public String path() {
        return "/api/v0/projects";
    }

    @Override
    public void validate(@NonNull AuthorizationExpression expression) {
        ProjectAclSpecifications.validate(expression);
    }
}

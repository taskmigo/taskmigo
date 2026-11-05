package io.taskmigo.identity.user.application;

import io.taskmigo.identity.user.application.api.UserQuery;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class UserAuthorizationTestSupport {

    private UserAuthorizationTestSupport() {}

    static UserQuery.List schemas() {
        return new UserQuery.List() {
            @Override
            public String operation() {
                return "user.query.list";
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of();
            }
        };
    }
}

package io.taskmigo.identity.user.application;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class UserAuthorizationTestSupport {

    private UserAuthorizationTestSupport() {}

    static UserQuerySchemas schemas() {
        QuerySchemaView list = schema("identity.users.list");
        return () -> list;
    }

    private static QuerySchemaView schema(String operation) {
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return operation;
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of();
            }
        };
    }
}

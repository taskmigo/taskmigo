package io.taskmigo.identity.user.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.application.api.ObjectAuthorization;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.List;

final class UserAuthorizationTestSupport {

    static final AuthorizationContext CONTEXT = new AuthorizationContext() {};

    private UserAuthorizationTestSupport() {}

    static List<QuerySchemaView> schemas() {
        return List.of(
            schema("identity.users.list"),
            schema("identity.users.delete"),
            schema("identity.users.update-statements")
        );
    }

    static ObjectAuthorization objectAuthorization(ObjectAuthorizationPredicate<UserInfo> predicate) {
        return new ObjectAuthorization() {
            @Override
            public <Q> ObjectAuthorizationPredicate<Q> authorize(
                AuthorizationContext context,
                QuerySchemaView schema,
                QueryFieldContext queryContext
            ) {
                return (ObjectAuthorizationPredicate<Q>) predicate;
            }

            @Override
            public void validatePolicy(String policy, String method, String path) {}
        };
    }

    static ObjectAuthorizationPredicate<UserInfo> allowAll() {
        return new ObjectAuthorizationPredicate<>() {
            @Override
            public boolean isAlwaysTrue() {
                return true;
            }

            @Override
            public boolean isAlwaysFalse() {
                return false;
            }
        };
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

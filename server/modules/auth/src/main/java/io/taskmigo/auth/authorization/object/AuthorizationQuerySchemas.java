package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.group.GroupInfo;
import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.user.UserInfo;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;

/// Registers the explicit API-visible query contracts owned by the authorization resource module.
@Configuration(proxyBeanMethods = false)
public class AuthorizationQuerySchemas {

    private static final ResolvableType STRING_TYPE = ResolvableType.forClass(String.class);
    private static final ResolvableType UUID_TYPE = ResolvableType.forClass(UUID.class);

    /// Registers the user collection query contract.
    @Bean
    QuerySchema<UserInfo> userQuerySchema() {
        return schema(UserInfo.class, List.of(
            field("id", UUID_TYPE), field("username", STRING_TYPE), field("firstName", STRING_TYPE), field("lastName", STRING_TYPE)
        ));
    }

    /// Registers the group collection query contract.
    @Bean
    QuerySchema<GroupInfo> groupQuerySchema() {
        return schema(GroupInfo.class, List.of(field("id", UUID_TYPE), field("name", STRING_TYPE), nullable("description")));
    }

    /// Registers the role collection query contract.
    @Bean
    QuerySchema<RoleInfo> roleQuerySchema() {
        return schema(RoleInfo.class, List.of(field("id", UUID_TYPE), field("name", STRING_TYPE), nullable("description")));
    }

    /// Registers the statement collection query contract, including composed API target paths.
    @Bean
    QuerySchema<StatementInfo> statementQuerySchema() {
        return schema(StatementInfo.class, List.of(
            field("id", UUID_TYPE), field("name", STRING_TYPE), nullable("description"),
            field("method", STRING_TYPE), field("path", STRING_TYPE)
        ));
    }

    private static QueryField field(String path, ResolvableType type) {
        return new QueryField(QueryPath.parse(path), type, false);
    }

    private static QueryField nullable(String path) {
        return new QueryField(QueryPath.parse(path), STRING_TYPE, true);
    }

    private static <Q> QuerySchema<Q> schema(Class<Q> type, Collection<QueryField> fields) {
        List<QueryField> declared = List.copyOf(fields);
        return new QuerySchema<>() {
            @Override
            public Class<Q> queryType() {
                return type;
            }

            @Override
            public Optional<QueryField> field(QueryPath path) {
                return declared.stream().filter(field -> field.path().equals(path)).findFirst();
            }

            @Override
            public Collection<QueryField> fields() {
                return declared;
            }
        };
    }
}

package io.taskmigo.identity.group.adapter.out.persistence;

import io.taskmigo.identity.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.query.QueryField;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchema;
import io.taskmigo.query.QuerySchemaView;
import java.util.Collection;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// Provides transitional transport contracts backed by the list-groups operation schema.
@Configuration(proxyBeanMethods = false)
class GroupQueryConfiguration {

    @Bean QuerySchema<GroupInfo> groupQuerySchema(ListGroupsQuerySchema schema) { return legacy(GroupInfo.class, schema); }
    @Bean QuerySchemaView groupObjectAuthorizationSchema(ListGroupsQuerySchema schema) { return objectView(GroupInfo.class, schema); }
    @Bean QueryPredicateBinder<GroupInfo, GroupEntity> groupQueryPredicateBinder(ListGroupsQuerySchema schema) { return new JpaQueryPredicateBinder<>(GroupInfo.class, schema); }
    @Bean ObjectAuthorizationPredicateBinder<GroupInfo, GroupEntity> groupObjectAuthorizationPredicateBinder(ListGroupsQuerySchema schema) { return new JpaObjectAuthorizationPredicateBinder<>(GroupInfo.class, schema); }

    private static <Q> QuerySchema<Q> legacy(Class<Q> type, QuerySchemaView view) {
        return new QuerySchema<>() {
            @Override public Class<Q> queryType() { return type; }
            @Override public Optional<QueryField> field(QueryPath path) { return this.fields().stream().filter(field -> field.path().equals(path)).findFirst(); }
            @Override public Collection<QueryField> fields() { return view.fields().stream().map(GroupQueryConfiguration::legacyField).toList(); }
            @Override public String identity() { return view.identity(); }
        };
    }

    private static QuerySchemaView objectView(Class<?> type, QuerySchemaView delegate) {
        return new QuerySchemaView() {
            @Override public String operation() { return type.getName(); }
            @Override public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) { return delegate.fields(context); }
            @Override public String identity(QueryFieldContext context) { return delegate.identity(context); }
        };
    }

    private static QueryField legacyField(QueryFieldDescriptor field) {
        return new QueryField(field.path(), field.type(), field.nullable(), field.operators());
    }
}

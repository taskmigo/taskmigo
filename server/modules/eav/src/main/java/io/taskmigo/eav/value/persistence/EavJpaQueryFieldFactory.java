package io.taskmigo.eav.value.persistence;

import io.taskmigo.eav.query.EavQueryField;
import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.jpaquery.JpaPath;
import io.taskmigo.jpaquery.JpaQueryValueExpression;
import io.taskmigo.jpaquery.QueryField;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class EavJpaQueryFieldFactory implements EavJpaQueryFieldProvider {

    @Override
    public <E> QueryField<E, ?> create(EavQueryField field, JpaPath<E, UUID> schemaInstancePath) {
        return switch (field.dataType()) {
            case STRING -> createTyped(field, schemaInstancePath, String.class, "stringValue");
            case NUMBER -> createTyped(field, schemaInstancePath, BigDecimal.class, "numberValue");
            case BOOLEAN -> createTyped(field, schemaInstancePath, Boolean.class, "booleanValue");
            case DATE -> createTyped(field, schemaInstancePath, LocalDate.class, "dateValue");
            case DATETIME -> createTyped(field, schemaInstancePath, Instant.class, "dateTimeValue");
            case UUID -> createTyped(field, schemaInstancePath, UUID.class, "uuidValue");
            case ENUM -> createTyped(field, schemaInstancePath, String.class, "enumValue");
        };
    }

    private static <E, V> QueryField<E, V> createTyped(
        EavQueryField field,
        JpaPath<E, UUID> schemaInstancePath,
        Class<V> javaType,
        String valueAttribute
    ) {
        JpaQueryValueExpression<E, V> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<V> resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                Subquery<V> subquery = query.subquery(javaType);
                Root<EavFieldValueEntity> value = subquery.from(EavFieldValueEntity.class);
                Expression<V> selected = value.get(valueAttribute).as(javaType);
                subquery.select(selected);
                subquery.where(
                    builder.equal(value.get("id").get("schemaInstanceId"), schemaInstancePath.resolve(root)),
                    builder.equal(value.get("id").get("fieldId"), field.fieldId())
                );
                return subquery;
            }

            @Override
            public Class<V> javaType() {
                return javaType;
            }
        };
        String sourceIdentity = field.fieldId() + ":" + field.dataType().name();
        QueryField<E, V> queryField = QueryField.dynamic(
            QueryPath.of(field.key()),
            TypeDescriptor.of(javaType),
            field.nullable(),
            javaType,
            expression,
            sourceIdentity
        ).operators(field.operators().toArray(QueryOperator[]::new));
        return field.sortable() ? queryField.sortable() : queryField;
    }
}

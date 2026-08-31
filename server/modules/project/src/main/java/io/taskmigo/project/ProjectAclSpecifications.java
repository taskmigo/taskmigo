package io.taskmigo.project;

import io.taskmigo.acl.AclExpression;
import io.taskmigo.acl.ApiAclEngine.ResponsePlan;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class ProjectAclSpecifications {

    private ProjectAclSpecifications() {}

    static Specification<ProjectEntity> from(ResponsePlan plan) {
        return (root, query, builder) ->
            predicate(plan.objectPredicate(), root, Objects.requireNonNull(query), builder);
    }

    private static Predicate predicate(
        AclExpression expression,
        Root<ProjectEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder builder
    ) {
        return switch (expression) {
            case AclExpression.Eq(var left, var right) -> builder.equal(
                value(left, root, builder),
                value(right, root, builder)
            );
            case AclExpression.Exists(var value) -> builder.isNotNull(value(value, root, builder));
            case AclExpression.All(var expressions) -> builder.and(
                expressions
                    .stream()
                    .map(item -> predicate(item, root, query, builder))
                    .toArray(Predicate[]::new)
            );
            case AclExpression.Any(var expressions) -> builder.or(
                expressions
                    .stream()
                    .map(item -> predicate(item, root, query, builder))
                    .toArray(Predicate[]::new)
            );
            case AclExpression.Not(var item) -> builder.not(predicate(item, root, query, builder));
            case AclExpression.Relation(var name, var principal, var object) -> relation(
                name,
                principal,
                object,
                root,
                query,
                builder
            );
        };
    }

    private static jakarta.persistence.criteria.Expression<?> value(
        AclExpression.Value value,
        Root<ProjectEntity> root,
        CriteriaBuilder builder
    ) {
        return switch (value) {
            case AclExpression.Literal(var literal) -> literal == null
                ? builder.nullLiteral(Object.class)
                : builder.literal(literal);
            case AclExpression.Ref(var path) -> root.get(objectAttribute(path));
        };
    }

    private static Predicate relation(
        String name,
        AclExpression.Value principal,
        AclExpression.Value object,
        Root<ProjectEntity> root,
        CriteriaQuery<?> query,
        CriteriaBuilder builder
    ) {
        if (!name.equals("projectMember")) throw new IllegalArgumentException(
            "Unsupported project ACL relation: " + name
        );
        if (
            !(principal instanceof AclExpression.Literal(var principalValue)) ||
            !(principalValue instanceof UUID userId)
        ) throw new IllegalArgumentException("projectMember principal must specialize to a UUID literal");
        if (
            !(object instanceof AclExpression.Ref(var objectPath)) || !objectPath.equals("object.id")
        ) throw new IllegalArgumentException("projectMember object must reference object.id");

        var subquery = query.subquery(Integer.class);
        var member = subquery.from(ProjectMemberEntity.class);
        subquery.select(builder.literal(1));
        subquery.where(
            builder.equal(member.get("projectId"), root.get("id")),
            builder.equal(member.get("principalType"), PrincipalType.USER),
            builder.equal(member.get("principalId"), userId)
        );
        return builder.exists(subquery);
    }

    private static String objectAttribute(String path) {
        if (!path.startsWith("object.")) throw new IllegalArgumentException("Expected object attribute, got: " + path);
        return path.substring("object.".length());
    }
}

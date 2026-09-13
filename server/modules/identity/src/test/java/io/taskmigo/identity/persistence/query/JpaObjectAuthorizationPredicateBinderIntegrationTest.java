package io.taskmigo.identity.persistence.query;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.persistence.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.persistence.ObjectAuthorizationPredicateModels;
import io.taskmigo.identity.persistence.user.UserEntity;
import io.taskmigo.identity.user.UserInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    classes = JpaObjectAuthorizationPredicateBinderIntegrationTest.TestApplication.class,
    properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=" +
            "io.taskmigo.identity.persistence.query.JpaObjectAuthorizationPredicateBinderIntegrationTest$SqlCaptureStatementInspector",
    }
)
@Testcontainers
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class JpaObjectAuthorizationPredicateBinderIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    private final EntityManager entityManager;

    JpaObjectAuthorizationPredicateBinderIntegrationTest(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @BeforeEach
    void clearCapturedSql() {
        SqlCaptureStatementInspector.clear();
    }

    /**
     * Verifies that a persistence-neutral Object Authorization predicate is translated through JPA into the expected
     * physical SQL predicate.
     *
     * Given: a User predicate requiring `firstName == lastName` and `username != firstName`, where the API-visible
     * `firstName` and `lastName` paths map to the `first_name` and `last_name` database columns.
     * Expect: Hibernate executes a query whose normalized WHERE clause is exactly
     * `first_name=last_name and username<>first_name`.
     */
    @Test
    @DisplayName("translates object authorization predicate to the expected SQL")
    void shouldTranslateToExpectedSqlWhenObjectAuthorizationPredicateIsBound() {
        // Arrange
        JpaObjectAuthorizationPredicateBinder<UserInfo, UserEntity> binder =
            new JpaObjectAuthorizationPredicateBinder<>(
                UserInfo.class,
                UserEntity.class,
                Map.of("firstName", "firstName", "lastName", "lastName", "username", "username"),
                Map.of("firstName", String.class, "lastName", String.class, "username", String.class)
            );
        ObjectAuthorizationPredicate<UserInfo> authorization = ObjectAuthorizationPredicateModels.wrap(
            "test-user-schema",
            new ObjectAuthorizationExpression.Binary(
                ObjectAuthorizationExpression.BinaryOperator.AND,
                comparison(ObjectAuthorizationExpression.BinaryOperator.EQUAL, "firstName", "lastName"),
                comparison(ObjectAuthorizationExpression.BinaryOperator.NOT_EQUAL, "username", "firstName")
            )
        );
        Specification<UserEntity> specification = binder.bind(authorization);

        // Act
        List<UserEntity> result = execute(specification);
        String whereClause = normalizedWhereClause(SqlCaptureStatementInspector.userSelect());

        // Assert
        assertThat(result).isEmpty();
        assertThat(whereClause).isEqualTo("first_name=last_name and username<>first_name");
    }

    private List<UserEntity> execute(Specification<UserEntity> specification) {
        CriteriaBuilder builder = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<UserEntity> query = builder.createQuery(UserEntity.class);
        Root<UserEntity> root = query.from(UserEntity.class);
        Predicate predicate = specification.toPredicate(root, query, builder);
        query.select(root).where(predicate);
        return this.entityManager.createQuery(query).getResultList();
    }

    private static ObjectAuthorizationExpression comparison(
        ObjectAuthorizationExpression.BinaryOperator operator,
        String left,
        String right
    ) {
        return new ObjectAuthorizationExpression.Binary(operator, reference(left), reference(right));
    }

    private static ObjectAuthorizationExpression reference(String path) {
        return new ObjectAuthorizationExpression.Reference("object", List.of(path));
    }

    private static String normalizedWhereClause(String sql) {
        String normalized = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        int whereIndex = normalized.indexOf(" where ");
        assertThat(whereIndex).as("captured SQL contains a WHERE clause").isGreaterThanOrEqualTo(0);
        return normalized
            .substring(whereIndex + " where ".length())
            .replaceAll("\\b[a-z][a-z0-9_]*\\d+_\\d+\\.", "")
            .replaceAll("\\s*(=|<>)\\s*", "$1")
            .replaceAll("\\s+", " ")
            .trim();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = UserEntity.class)
    static class TestApplication {}

    public static final class SqlCaptureStatementInspector implements StatementInspector {

        private static final List<String> SQL = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            SQL.add(sql);
            return sql;
        }

        static void clear() {
            SQL.clear();
        }

        static String userSelect() {
            return SQL.stream()
                .filter(sql -> sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").contains(" from users "))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SELECT from users was captured: " + SQL));
        }
    }
}

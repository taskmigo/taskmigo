package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.adapter.out.persistence.query.JpaObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.JpaQueryPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserProfile;
import io.taskmigo.jpaquery.JpaQuerySpecifications;
import io.taskmigo.query.QueryPredicate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Reads User projections and resolves mutation targets through operation-scoped JPA query schemas.
@Repository
public class JpaUserQueryRepository implements UserQueryRepository {

    private final JpaUserRepository users;
    private final QueryPredicateBinder<UserInfo, UserEntity> listFilterBinder;
    private final ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> listAuthorizationBinder;
    private final ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> deleteAuthorizationBinder;
    private final ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> statementUpdateAuthorizationBinder;

    JpaUserQueryRepository(
        JpaUserRepository users,
        ListUsersQuerySchema listSchema,
        DeleteUserQuerySchema deleteSchema,
        UpdateUserStatementsQuerySchema statementUpdateSchema
    ) {
        this.users = users;
        this.listFilterBinder = new JpaQueryPredicateBinder<>(UserInfo.class, listSchema);
        this.listAuthorizationBinder = new JpaObjectAuthorizationPredicateBinder<>(UserInfo.class, listSchema);
        this.deleteAuthorizationBinder = new JpaObjectAuthorizationPredicateBinder<>(UserInfo.class, deleteSchema);
        this.statementUpdateAuthorizationBinder = new JpaObjectAuthorizationPredicateBinder<>(
            UserInfo.class,
            statementUpdateSchema
        );
    }

    @Override
    public Optional<UserInfo> find(UUID id) {
        return this.users
            .findById(id)
            .filter(user -> user.status() != UserStatus.TOMBSTONE)
            .map(JpaUserQueryRepository::info);
    }

    @Override
    public Optional<User> findForDelete(UUID id, ObjectAuthorizationPredicate<UserInfo> authorization) {
        return this.authorizedFind(id, authorization, this.deleteAuthorizationBinder);
    }

    @Override
    public Optional<User> findForStatementUpdate(
        UUID id,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        return this.authorizedFind(id, authorization, this.statementUpdateAuthorizationBinder);
    }

    @Override
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users.findByUsername(username).map(JpaUserQueryRepository::authentication);
    }

    @Override
    public boolean exists(UUID id) {
        return this.users.findById(id).filter(user -> user.status() != UserStatus.TOMBSTONE).isPresent();
    }

    @Override
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        Specification<UserEntity> visible = (root, query, builder) ->
            builder.notEqual(root.get(UserEntity_.status), UserStatus.TOMBSTONE);
        Specification<UserEntity> authorizationSpec = this.listAuthorizationBinder.bind(authorization);
        Specification<UserEntity> clientFilter = this.listFilterBinder.bind(filter);
        Specification<UserEntity> authorized = JpaQuerySpecifications.authorized(
            authorizationSpec,
            clientFilter
        );
        var result = this.users.findAll(visible.and(authorized), pageable);
        return new OffsetPage<>(
            result.map(JpaUserQueryRepository::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private Optional<User> authorizedFind(
        UUID id,
        ObjectAuthorizationPredicate<UserInfo> authorization,
        ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> binder
    ) {
        Specification<UserEntity> target = (root, query, builder) ->
            builder.and(
                builder.equal(root.get(UserEntity_.id), id),
                builder.notEqual(root.get(UserEntity_.status), UserStatus.TOMBSTONE)
            );
        Specification<UserEntity> authorizationSpec = binder.bind(authorization);
        return this.users.findOne(target.and(authorizationSpec)).map(UserEntity::toDomain);
    }

    private static UserInfo info(UserEntity user) {
        UserProfile profile = UserProfile.of(
            user.emails(),
            Objects.requireNonNull(user.firstName()),
            Objects.requireNonNull(user.lastName())
        );
        return new UserInfo(
            user.id(),
            Objects.requireNonNull(user.username()),
            profile.firstName(),
            profile.lastName(),
            profile.emails(),
            profile.displayName(),
            user.status(),
            user.retainedAt()
        );
    }

    private static AuthenticationInfo authentication(UserEntity user) {
        UserProfile profile = UserProfile.of(
            null,
            Objects.requireNonNull(user.firstName()),
            Objects.requireNonNull(user.lastName())
        );
        return new AuthenticationInfo(
            user.id(),
            Objects.requireNonNull(user.username()),
            profile.displayName(),
            UserStatus.ACTIVE.equals(user.status()),
            user.passwordHash()
        );
    }
}

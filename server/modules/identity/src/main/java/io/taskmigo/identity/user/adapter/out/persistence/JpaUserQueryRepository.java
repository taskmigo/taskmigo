package io.taskmigo.identity.user.adapter.out.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.adapter.out.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.adapter.out.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.AuthenticationInfo;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import io.taskmigo.identity.user.domain.UserProfile;
import io.taskmigo.query.QueryPredicate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/// Reads User projections directly from JPA without hydrating the mutation aggregate.
@Repository
public class JpaUserQueryRepository implements UserQueryRepository {

    private final JpaUserRepository users;
    private final QueryPredicateBinder<UserInfo, UserEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder;

    public JpaUserQueryRepository(
        JpaUserRepository users,
        QueryPredicateBinder<UserInfo, UserEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder
    ) {
        this.users = users;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public Optional<UserInfo> find(UUID id) {
        return this.users.findById(id).filter(user -> user.status() != UserStatus.PURGED).map(JpaUserQueryRepository::info);
    }

    @Override
    public Optional<UserInfo> find(UUID id, ObjectAuthorizationPredicate<UserInfo> authorization) {
        Specification<UserEntity> idMatch = (root, query, builder) -> builder.and(builder.equal(root.get("id"), id), builder.notEqual(root.get("status"), UserStatus.PURGED));
        return this.users.findOne(idMatch.and(this.objectBinder.bind(authorization))).map(JpaUserQueryRepository::info);
    }

    @Override
    public Optional<AuthenticationInfo> findForAuthentication(String username) {
        return this.users.findByUsername(username).map(JpaUserQueryRepository::authentication);
    }

    @Override
    public boolean exists(UUID id) {
        return this.users.existsById(id);
    }

    @Override
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        Specification<UserEntity> visible = (root, query, builder) -> builder.notEqual(root.get("status"), UserStatus.PURGED);
        var result = this.users.findAll(
            visible.and(this.queryBinder.bind(filter)).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaUserQueryRepository::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private static UserInfo info(UserEntity user) {
        UserProfile profile = UserProfile.of(user.emails(), user.firstName(), user.lastName());
        return new UserInfo(
            user.id(),
            user.username(),
            profile.firstName(),
            profile.lastName(),
            profile.emails(),
            profile.displayName(),
            user.status(),
            user.retainedAt()
        );
    }

    private static AuthenticationInfo authentication(UserEntity user) {
        UserProfile profile = UserProfile.of(null, user.firstName(), user.lastName());
        return new AuthenticationInfo(
            user.id(),
            user.username(),
            profile.displayName(),
            UserStatus.ACTIVE.equals(user.status()),
            user.passwordHash()
        );
    }
}

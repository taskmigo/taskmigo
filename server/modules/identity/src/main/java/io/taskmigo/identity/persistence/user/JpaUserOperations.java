package io.taskmigo.identity.persistence.user;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.persistence.query.ObjectAuthorizationPredicateBinder;
import io.taskmigo.identity.persistence.query.QueryPredicateBinder;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.internal.UserStore;
import io.taskmigo.identity.user.internal.UserStore.UserState;
import io.taskmigo.query.QueryPredicate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/// Implements User persistence operations with JPA entities and repositories.
@Service
public class JpaUserOperations implements UserStore {

    private final UserRepository users;
    private final QueryPredicateBinder<UserInfo, UserEntity> queryBinder;
    private final ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder;

    public JpaUserOperations(
        UserRepository users,
        QueryPredicateBinder<UserInfo, UserEntity> queryBinder,
        ObjectAuthorizationPredicateBinder<UserInfo, UserEntity> objectBinder
    ) {
        this.users = users;
        this.queryBinder = queryBinder;
        this.objectBinder = objectBinder;
    }

    @Override
    public Optional<UserState> find(UUID id) {
        return this.users.findById(id).map(JpaUserOperations::state);
    }

    @Override
    public Optional<UserState> findByUsername(String username) {
        return this.users.findByUsername(username).map(JpaUserOperations::state);
    }

    @Override
    public void create(UserState user) {
        try {
            UserEntity entity = new UserEntity(
                user.id(),
                user.username(),
                user.emails(),
                user.firstName(),
                user.lastName()
            );
            if (user.passwordHash() != null) {
                entity.setPasswordHash(user.passwordHash());
            }
            this.users.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new UserException(UserException.Type.CONFLICT, "Username or email already exists", exception);
        }
    }

    @Override
    public void delete(UUID id) {
        this.users.deleteById(id);
        this.users.flush();
    }

    @Override
    public void updateProfile(UUID id, Set<String> emails, String firstName, String lastName) {
        UserEntity user = this.users.findById(id).orElseThrow();
        user.replaceEmails(emails);
        user.updateProfile(firstName, lastName);
        this.users.flush();
    }

    @Override
    public void updatePasswordHash(UUID id, String passwordHash) {
        UserEntity user = this.users.findById(id).orElseThrow();
        user.setPasswordHash(passwordHash);
        this.users.flush();
    }

    @Override
    public OffsetPage<UserInfo> list(
        int page,
        int perPage,
        QueryPredicate<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        var pageable = PageRequest.of(page - 1, perPage, Sort.by("id"));
        var result = this.users.findAll(
            this.queryBinder.bind(filter).and(this.objectBinder.bind(authorization)),
            pageable
        );
        return new OffsetPage<>(
            result.map(JpaUserOperations::info).getContent(),
            result.getTotalElements(),
            result.getTotalPages()
        );
    }

    private static UserInfo info(UserEntity user) {
        return new UserInfo(
            user.id(),
            user.username(),
            user.firstName(),
            user.lastName(),
            user.emails(),
            user.displayName()
        );
    }

    private static UserState state(UserEntity user) {
        return new UserState(
            user.id(),
            user.username(),
            user.emails(),
            user.firstName(),
            user.lastName(),
            UserStatus.ACTIVE.equals(user.status()),
            user.passwordHash()
        );
    }
}

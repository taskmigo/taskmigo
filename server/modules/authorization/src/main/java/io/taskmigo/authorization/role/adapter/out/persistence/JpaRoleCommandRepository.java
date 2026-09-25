package io.taskmigo.authorization.role.adapter.out.persistence;

import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.application.port.out.RoleCommandRepository;
import io.taskmigo.authorization.role.domain.Role;
import io.taskmigo.authorization.role.domain.RoleCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/// Adapts canonical Role aggregate persistence to JPA without loading hierarchy state.
@Repository
public class JpaRoleCommandRepository implements RoleCommandRepository {

    private final RoleRepository roles;

    public JpaRoleCommandRepository(RoleRepository roles) {
        this.roles = roles;
    }

    @Override
    public Optional<Role> find(UUID id) {
        return this.roles.findDistinctById(id).map(RoleEntity::toDomain);
    }

    @Override
    public Optional<Role> findByCode(RoleCode code) {
        return this.roles.findByCode(code.value()).map(RoleEntity::toDomain);
    }

    @Override
    public void save(Role role) {
        RoleEntity existing = this.roles.findDistinctById(role.id()).orElse(null);
        if (existing == null) {
            try {
                this.roles.saveAndFlush(RoleEntity.from(role));
            } catch (DataIntegrityViolationException exception) {
                throw new RoleException(RoleException.Type.CONFLICT, "Role code already exists", exception);
            }
            return;
        }
        existing.update(role);
        this.roles.flush();
    }

    @Override
    public void delete(Role role) {
        this.roles.deleteById(role.id());
        this.roles.flush();
    }
}

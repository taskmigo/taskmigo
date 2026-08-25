package io.taskmigo.organization;

import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Manages organization lifecycle operations and exposes organization identity to collaborating modules.
@Service
public class OrganizationService {

    private final OrganizationRepository organizations;

    OrganizationService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /// Creates an organization after trimming required text fields and enforcing key uniqueness.
    @Transactional
    public UUID create(@Nullable String key, @Nullable String name) {
        try {
            UUID id = UUID.randomUUID();
            this.organizations.saveAndFlush(new OrganizationEntity(id, required(key, "key"), required(name, "name")));
            return id;
        } catch (DataIntegrityViolationException exception) {
            throw new OrganizationException(
                OrganizationException.Type.CONFLICT,
                "Organization key already exists",
                exception
            );
        }
    }

    /// Verifies that an organization exists.
    @Transactional(readOnly = true)
    public void require(UUID id) {
        if (!this.organizations.existsById(id)) {
            throw new OrganizationException(OrganizationException.Type.NOT_FOUND, "Organization not found");
        }
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw new OrganizationException(OrganizationException.Type.BAD_REQUEST, field + " is required");
        }
        return value.trim();
    }
}

package io.taskmigo.resource;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {}

package io.taskmigo.authorization.persistence.subject;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectStatementBindingRepository extends JpaRepository<SubjectStatementBindingEntity, UUID> {
    List<SubjectStatementBindingEntity> findAllBySubjectTypeAndSubjectId(String subjectType, UUID subjectId);

    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from SubjectStatementBindingEntity binding
        where binding.subjectType = :subjectType and binding.subjectId = :subjectId
        """
    )
    void deleteAllBySubjectTypeAndSubjectId(
        @Param("subjectType") String subjectType,
        @Param("subjectId") UUID subjectId
    );
}

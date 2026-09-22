package io.taskmigo.authorization.subject.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRoleBindingRepository extends JpaRepository<SubjectRoleBindingEntity, UUID> {
    List<SubjectRoleBindingEntity> findAllBySubjectTypeAndSubjectId(String subjectType, UUID subjectId);

    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Modifying(flushAutomatically = true)
    @Query(
        """
        delete from SubjectRoleBindingEntity binding
        where binding.subjectType = :subjectType and binding.subjectId = :subjectId
        """
    )
    void deleteAllBySubjectTypeAndSubjectId(
        @Param("subjectType") String subjectType,
        @Param("subjectId") UUID subjectId
    );
}

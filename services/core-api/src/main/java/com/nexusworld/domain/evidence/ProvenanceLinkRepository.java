package com.nexusworld.domain.evidence;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProvenanceLinkRepository extends JpaRepository<ProvenanceLink, UUID> {
    List<ProvenanceLink> findBySubjectTypeAndSubjectIdOrderByCreatedAtAsc(String subjectType, UUID subjectId);
}

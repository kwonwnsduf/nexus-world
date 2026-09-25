package com.nexusworld.domain.evidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EvidenceItemRepository extends JpaRepository<EvidenceItem, UUID> {
  Optional<EvidenceItem> findFirstBySource_IdOrderByCreatedAtAsc(UUID sourceId);
}

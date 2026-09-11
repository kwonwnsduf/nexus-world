package com.nexusworld.domain.evidence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AssumptionRepository extends JpaRepository<Assumption, UUID> { boolean existsByAssumptionKey(String assumptionKey); }

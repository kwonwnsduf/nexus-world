package com.nexusworld.domain.evidence;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DataSourceRepository extends JpaRepository<DataSource, UUID> { boolean existsBySourceKey(String sourceKey); }

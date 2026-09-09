package com.nexusworld.domain.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByNormalizedUsername(String normalizedUsername);

    boolean existsByNormalizedUsername(String normalizedUsername);
}

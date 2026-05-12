package com.cacanode.api.auth.repository;

import com.cacanode.api.auth.model.VerificationResendState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationResendStateRepository extends JpaRepository<VerificationResendState, UUID> {
    Optional<VerificationResendState> findByUserId(UUID userId);
}

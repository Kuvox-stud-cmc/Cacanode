package com.cacanode.api.auth.repository;

import com.cacanode.api.auth.model.Login2FAState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface Login2FAStateRepository extends JpaRepository<Login2FAState, UUID> {
    Optional<Login2FAState> findByTokenHash(String tokenHash);

    Optional<Login2FAState> findByUserId(UUID userId);

    Optional<Login2FAState> findByEmail(String email);
}

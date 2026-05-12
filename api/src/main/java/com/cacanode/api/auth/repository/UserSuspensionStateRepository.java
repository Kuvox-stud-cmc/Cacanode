package com.cacanode.api.auth.repository;

import com.cacanode.api.auth.model.UserSuspensionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSuspensionStateRepository extends JpaRepository<UserSuspensionState, UUID> {
    Optional<UserSuspensionState> findByUserId(UUID userId);
}

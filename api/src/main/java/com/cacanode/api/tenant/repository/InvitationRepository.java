package com.cacanode.api.tenant.repository;

import com.cacanode.api.tenant.model.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {
}

package com.cacanode.api.tenant.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.tenant.enums.InvitationStatus;
import com.cacanode.api.tenant.enums.UserStatus;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.repository.InvitationRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantEntitlementService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;

    public void assertCanAddMember(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Integer limit = tenant.getMaxTeamMembers();
        if (limit == null) return;
        long current = activeMembers(tenantId) + pendingInvitations(tenantId);
        if (current >= limit) {
            throw new BadRequestException("TEAM_MEMBER_QUOTA_EXCEEDED");
        }
    }

    public void assertCanAcceptInvitation(UUID tenantId) {
        Tenant tenant = tenantRepository.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Integer limit = tenant.getMaxTeamMembers();
        if (limit == null) return;
        long current = activeMembers(tenantId) + pendingInvitations(tenantId);
        if (current > limit) {
            throw new BadRequestException("TEAM_MEMBER_QUOTA_EXCEEDED");
        }
    }

    private long activeMembers(UUID tenantId) {
        return userRepository.countByTenant_IdAndStatus(tenantId, UserStatus.ACTIVE);
    }

    private long pendingInvitations(UUID tenantId) {
        return invitationRepository.countByTenant_IdAndStatusAndExpiresAtAfter(
                tenantId, InvitationStatus.PENDING, LocalDateTime.now());
    }
}

package com.cacanode.api.tenant.service.implement;

import com.cacanode.api.tenant.api.TenantAnalyticsExportApi;
import com.cacanode.api.tenant.repository.InvitationRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantAnalyticsExportApiImpl implements TenantAnalyticsExportApi {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;

    @Override
    public SnapshotPage<TenantSnapshot> tenants(int page, int size) {
        var result = tenantRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new SnapshotPage<>(result.stream().map(value -> new TenantSnapshot(
                value.getId(), value.getName(), value.getStatus().name(), value.getPlan().name(),
                value.getMaxStorageMb() == null ? 0 : value.getMaxStorageMb(),
                value.getCreatedAt(), value.getUpdatedAt(), value.getKind())).toList(), result.hasNext());
    }

    @Override
    public SnapshotPage<UserSnapshot> users(int page, int size) {
        var result = userRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new SnapshotPage<>(result.stream().map(value -> new UserSnapshot(
                value.getId(), value.getTenant().getId(), value.getStatus().name(),
                value.getRole().name(), value.getCreatedAt(), value.getUpdatedAt())).toList(), result.hasNext());
    }

    @Override
    public SnapshotPage<InvitationSnapshot> invitations(int page, int size) {
        var result = invitationRepository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new SnapshotPage<>(result.stream().map(value -> new InvitationSnapshot(
                value.getId(), value.getTenant().getId(), value.getStatus().name(), value.getCreatedAt(),
                value.getExpiresAt(), value.getLastSentAt() == null ? value.getCreatedAt() : value.getLastSentAt()))
                .toList(), result.hasNext());
    }
}

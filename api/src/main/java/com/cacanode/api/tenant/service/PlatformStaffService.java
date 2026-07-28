package com.cacanode.api.tenant.service;

import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.durable.DurableEventPublisher;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.ResourceNotFoundException;
import com.cacanode.api.common.service.SynchronousAuditRecorder;
import com.cacanode.api.tenant.CustomerAnswerPromptDefaults;
import com.cacanode.api.tenant.api.PlatformIdentityApi;
import com.cacanode.api.tenant.api.PlatformStaffApi;
import com.cacanode.api.tenant.api.TenantKind;
import com.cacanode.api.tenant.api.TenantPlan;
import com.cacanode.api.tenant.api.TenantStatus;
import com.cacanode.api.tenant.api.event.TenantCreatedEvent;
import com.cacanode.api.tenant.api.event.UserDeactivatedEvent;
import com.cacanode.api.tenant.api.event.UserInvitedEvent;
import com.cacanode.api.tenant.api.event.UserProjectionChangedEvent;
import com.cacanode.api.tenant.api.event.InvitationProjectionChangedEvent;
import com.cacanode.api.tenant.enums.InvitationStatus;
import com.cacanode.api.tenant.enums.UserRole;
import com.cacanode.api.tenant.enums.UserStatus;
import com.cacanode.api.tenant.model.Invitation;
import com.cacanode.api.tenant.model.Tenant;
import com.cacanode.api.tenant.model.User;
import com.cacanode.api.tenant.repository.InvitationRepository;
import com.cacanode.api.tenant.repository.TenantRepository;
import com.cacanode.api.tenant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformStaffService implements PlatformStaffApi {
    private static final String PLATFORM_NAME = "CacaNode Platform";
    private static final String PLATFORM_SLUG = "cacanode-platform";
    private static final Duration INVITATION_LIFETIME = Duration.ofHours(72);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantRepository tenants;
    private final UserRepository users;
    private final InvitationRepository invitations;
    private final TenantUserManagementService invitationTokens;
    private final PlatformIdentityApi identities;
    private final SynchronousAuditRecorder audits;
    private final ApplicationEventPublisher events;
    @Autowired(required = false) private DurableEventPublisher durableEvents;

    @Override
    @Transactional
    public SeedResult seedFirstAdministrator(String rawEmail, String rawName, String passwordHash) {
        String email = normalizeEmail(rawEmail);
        String name = rawName == null ? "" : rawName.trim();
        if (email.isBlank() || name.isBlank() || passwordHash == null || passwordHash.isBlank()) {
            throw new BadRequestException("Email, full name, and password hash are required");
        }
        Tenant existingTenant = tenants.findByKind(TenantKind.PLATFORM_INTERNAL).orElse(null);
        User existingUser = users.findByEmailIgnoreCase(email).orElse(null);
        if (existingTenant != null) {
            if (existingUser != null && existingUser.getTenant().getId().equals(existingTenant.getId())
                    && existingUser.getRole() == UserRole.PLATFORM_ADMIN
                    && existingUser.getStatus() == UserStatus.ACTIVE) {
                return new SeedResult(existingTenant.getId(), existingUser.getId(), false);
            }
            throw new ConflictException("The platform has already been seeded with a different administrator");
        }
        if (existingUser != null) {
            throw new ConflictException("Email already belongs to another account");
        }

        LocalDateTime now = LocalDateTime.now();
        Tenant tenant = new Tenant();
        tenant.setName(PLATFORM_NAME);
        tenant.setSlug(PLATFORM_SLUG);
        tenant.setKind(TenantKind.PLATFORM_INTERNAL);
        tenant.setPlan(TenantPlan.ENTERPRISE);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setMaxDocuments(0);
        tenant.setMaxMessages(0);
        tenant.setMaxStorageMb(0);
        tenant.setMaxTeamMembers(0);
        tenant.setApiAccessEnabled(false);
        tenant.setWebhooksEnabled(false);
        tenant.setAdvancedAnalyticsEnabled(false);
        tenant.setCustomBrandingEnabled(false);
        tenant.setCustomerAnswerPrompt(CustomerAnswerPromptDefaults.PLATFORM_DEFAULT);
        tenants.saveAndFlush(tenant);

        User user = new User();
        user.setTenant(tenant);
        user.setEmail(email);
        user.setFullName(name);
        user.setPasswordHash(passwordHash);
        user.setRole(UserRole.PLATFORM_ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        users.saveAndFlush(user);

        publish("tenant.created.v1", new TenantCreatedEvent(tenant.getId(), user.getId(), null, null,
                tenant.getName(), tenant.getStatus().name(), tenant.getPlan().name(), 0, now, tenant.getKind()));
        publishUser(user);
        audits.record(tenant.getId(), user.getId(), LogAction.PLATFORM_ADMIN_SEEDED,
                "platform_staff", user.getId(), null, null, Map.of("transition", "UNSEEDED->ACTIVE"));
        return new SeedResult(tenant.getId(), user.getId(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<StaffItem> staff(UUID actorTenantId, UUID actorId, ListQuery query) {
        identities.requirePlatformAdministrator(actorTenantId, actorId);
        ListQuery valid = validateQuery(query, List.of("name", "email", "status", "createdAt", "lastLoginAt"));
        List<User> values = new ArrayList<>(users.findByTenant_IdOrderByFullNameAsc(actorTenantId));
        String needle = valid.q().toLowerCase(Locale.ROOT);
        values.removeIf(value -> !needle.isBlank() && !(safe(value.getFullName()).toLowerCase(Locale.ROOT).contains(needle)
                || value.getEmail().toLowerCase(Locale.ROOT).contains(needle)));
        if (!valid.status().isBlank()) {
            UserStatus status = parseUserStatus(valid.status());
            values.removeIf(value -> value.getStatus() != status);
        }
        values.sort(staffComparator(valid));
        return page(values.stream().map(value -> toStaff(value, actorId)).toList(), valid);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<InvitationItem> invitations(UUID actorTenantId, UUID actorId, ListQuery query) {
        identities.requirePlatformAdministrator(actorTenantId, actorId);
        ListQuery valid = validateQuery(query, List.of("email", "status", "createdAt", "expiresAt", "lastSentAt"));
        LocalDateTime now = LocalDateTime.now();
        List<InvitationItem> values = invitations.findByTenant_IdOrderByCreatedAtDesc(actorTenantId).stream()
                .map(value -> toInvitation(value, now)).filter(value -> valid.q().isBlank()
                        || value.email().toLowerCase(Locale.ROOT).contains(valid.q().toLowerCase(Locale.ROOT)))
                .filter(value -> valid.status().isBlank() || value.status().equals(valid.status()))
                .sorted(invitationComparator(valid)).toList();
        return page(values, valid);
    }

    @Override
    @Transactional
    public InvitationItem invite(UUID tenantId, UUID actorId, String rawEmail, String ip, String userAgent) {
        Tenant tenant = lockAndAuthorize(tenantId, actorId);
        String email = normalizeEmail(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) throw new ConflictException("An account already exists for this email");
        LocalDateTime now = LocalDateTime.now();
        invitations.findFirstByTenant_IdAndEmailIgnoreCaseAndStatus(tenantId, email, InvitationStatus.PENDING)
                .ifPresent(value -> {
                    if (value.getExpiresAt().isAfter(now)) {
                        throw new ConflictException("A pending invitation already exists for this email");
                    }
                    value.setStatus(InvitationStatus.EXPIRED);
                    invitations.saveAndFlush(value);
                    publishInvitationProjection(value);
                });
        String token = token();
        Invitation value = new Invitation();
        value.setTenant(tenant);
        value.setInvitedBy(users.findByIdAndTenant_Id(actorId, tenantId).orElseThrow());
        value.setEmail(email);
        value.setRole(UserRole.PLATFORM_ADMIN);
        value.setTokenHash(invitationTokens.hashToken(token));
        value.setStatus(InvitationStatus.PENDING);
        value.setLastSentAt(now);
        value.setExpiresAt(now.plus(INVITATION_LIFETIME));
        invitations.saveAndFlush(value);
        publishInvitation(value, token);
        audits.record(tenantId, actorId, LogAction.PLATFORM_STAFF_INVITED, "invitation", value.getId(),
                ip, userAgent, Map.of("transition", "NONE->PENDING", "targetId", value.getId().toString()));
        return toInvitation(value, now);
    }

    @Override
    @Transactional
    public InvitationItem resend(UUID tenantId, UUID actorId, UUID invitationId, String ip, String userAgent) {
        lockAndAuthorize(tenantId, actorId);
        Invitation value = requireInvitation(tenantId, invitationId);
        if (value.getStatus() != InvitationStatus.PENDING && value.getStatus() != InvitationStatus.EXPIRED) {
            throw new BadRequestException("Only pending or expired invitations can be resent");
        }
        LocalDateTime now = LocalDateTime.now();
        if (value.getLastSentAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new BadRequestException("Please wait before resending");
        }
        String token = token();
        value.setTokenHash(invitationTokens.hashToken(token));
        value.setStatus(InvitationStatus.PENDING);
        value.setLastSentAt(now);
        value.setExpiresAt(now.plus(INVITATION_LIFETIME));
        invitations.save(value);
        publishInvitation(value, token);
        audits.record(tenantId, actorId, LogAction.PLATFORM_STAFF_INVITATION_RESENT, "invitation",
                value.getId(), ip, userAgent, Map.of("transition", "EXPIRED_OR_PENDING->PENDING"));
        return toInvitation(value, now);
    }

    @Override
    @Transactional
    public void cancel(UUID tenantId, UUID actorId, UUID invitationId, String ip, String userAgent) {
        lockAndAuthorize(tenantId, actorId);
        Invitation value = requireInvitation(tenantId, invitationId);
        if (value.getStatus() != InvitationStatus.PENDING || !value.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Only pending invitations can be cancelled");
        }
        value.setStatus(InvitationStatus.CANCELLED);
        invitations.save(value);
        publishInvitationProjection(value);
        audits.record(tenantId, actorId, LogAction.PLATFORM_STAFF_INVITATION_CANCELLED, "invitation",
                value.getId(), ip, userAgent, Map.of("transition", "PENDING->CANCELLED"));
    }

    @Override
    @Transactional
    public StaffItem updateStatus(UUID tenantId, UUID actorId, UUID staffId, String requested,
                                  String ip, String userAgent) {
        lockAndAuthorize(tenantId, actorId);
        UserStatus status;
        try { status = UserStatus.valueOf(requested); }
        catch (Exception exception) { throw new BadRequestException("Status must be ACTIVE or INACTIVE"); }
        if (status != UserStatus.ACTIVE && status != UserStatus.INACTIVE) throw new BadRequestException("Status must be ACTIVE or INACTIVE");
        if (actorId.equals(staffId) && status == UserStatus.INACTIVE) throw new BadRequestException("You cannot deactivate your own account");
        User staff = users.findByIdAndTenant_Id(staffId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform employee not found"));
        if (staff.getRole() != UserRole.PLATFORM_ADMIN) throw new ConflictException("Platform employee role is invalid");
        if (staff.getStatus() == status) return toStaff(staff, actorId);
        if (status == UserStatus.ACTIVE && staff.getStatus() != UserStatus.INACTIVE) {
            throw new BadRequestException("Only inactive employees can be reactivated");
        }
        if (status == UserStatus.INACTIVE && users.countByTenant_IdAndRoleAndStatus(
                tenantId, UserRole.PLATFORM_ADMIN, UserStatus.ACTIVE) <= 1) {
            throw new BadRequestException("The final active platform administrator cannot be deactivated");
        }
        UserStatus previous = staff.getStatus();
        staff.setStatus(status);
        users.save(staff);
        publishUser(staff);
        if (status == UserStatus.INACTIVE) publish("tenant.user.deactivated.v1", new UserDeactivatedEvent(tenantId, staffId));
        audits.record(tenantId, actorId, status == UserStatus.ACTIVE
                        ? LogAction.PLATFORM_STAFF_REACTIVATED : LogAction.PLATFORM_STAFF_DEACTIVATED,
                "platform_staff", staffId, ip, userAgent,
                Map.of("transition", previous.name() + "->" + status.name(), "targetId", staffId.toString()));
        return toStaff(staff, actorId);
    }

    private Tenant lockAndAuthorize(UUID tenantId, UUID actorId) {
        identities.requirePlatformAdministrator(tenantId, actorId);
        Tenant tenant = tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Platform tenant not found"));
        if (tenant.getKind() != TenantKind.PLATFORM_INTERNAL) throw new ConflictException("Platform tenant is invalid");
        return tenant;
    }

    private Invitation requireInvitation(UUID tenantId, UUID id) {
        return invitations.findByIdAndTenant_Id(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
    }

    private ListQuery validateQuery(ListQuery query, List<String> sorts) {
        int page = query == null ? 0 : query.page();
        int size = query == null ? 20 : query.size();
        if (page < 0 || size < 1 || size > 100) throw new BadRequestException("Invalid pagination parameters");
        String q = query.q() == null ? "" : query.q().trim();
        if (q.length() > 200) q = q.substring(0, 200);
        String status = query.status() == null ? "" : query.status().trim().toUpperCase(Locale.ROOT);
        String sort = query.sort() == null || query.sort().isBlank() ? "createdAt" : query.sort();
        String direction = query.direction() == null || query.direction().isBlank() ? "desc" : query.direction().toLowerCase(Locale.ROOT);
        if (!sorts.contains(sort) || !(direction.equals("asc") || direction.equals("desc"))) {
            throw new BadRequestException("Invalid sort parameters");
        }
        return new ListQuery(page, size, q, status, sort, direction);
    }

    private UserStatus parseUserStatus(String status) {
        try { return UserStatus.valueOf(status); }
        catch (Exception exception) { throw new BadRequestException("Invalid staff status"); }
    }

    private Comparator<User> staffComparator(ListQuery query) {
        Comparator<User> comparator = switch (query.sort()) {
            case "name" -> Comparator.comparing(value -> safe(value.getFullName()), String.CASE_INSENSITIVE_ORDER);
            case "email" -> Comparator.comparing(User::getEmail, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(value -> value.getStatus().name());
            case "lastLoginAt" -> Comparator.comparing(User::getLastLoginAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        comparator = comparator.thenComparing(User::getId);
        return query.direction().equals("desc") ? comparator.reversed() : comparator;
    }

    private Comparator<InvitationItem> invitationComparator(ListQuery query) {
        Comparator<InvitationItem> comparator = switch (query.sort()) {
            case "email" -> Comparator.comparing(InvitationItem::email, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(InvitationItem::status);
            case "expiresAt" -> Comparator.comparing(InvitationItem::expiresAt);
            case "lastSentAt" -> Comparator.comparing(InvitationItem::lastSentAt);
            default -> Comparator.comparing(InvitationItem::createdAt);
        };
        comparator = comparator.thenComparing(InvitationItem::id);
        return query.direction().equals("desc") ? comparator.reversed() : comparator;
    }

    private <T> PageResult<T> page(List<T> values, ListQuery query) {
        int start = Math.min(values.size(), query.page() * query.size());
        int end = Math.min(values.size(), start + query.size());
        return new PageResult<>(values.subList(start, end), query.page(), query.size(), values.size());
    }

    private StaffItem toStaff(User value, UUID actorId) {
        return new StaffItem(value.getId(), value.getEmail(), value.getFullName(), value.getRole().name(),
                value.getStatus().name(), value.getCreatedAt(), value.getLastLoginAt(), value.getId().equals(actorId));
    }

    private InvitationItem toInvitation(Invitation value, LocalDateTime now) {
        String status = value.getStatus() == InvitationStatus.PENDING && !value.getExpiresAt().isAfter(now)
                ? InvitationStatus.EXPIRED.name() : value.getStatus().name();
        return new InvitationItem(value.getId(), value.getEmail(), value.getRole().name(), status,
                value.getCreatedAt(), value.getExpiresAt(), value.getLastSentAt());
    }

    private void publishInvitation(Invitation value, String token) {
        publish("tenant.user.invited.v1", new UserInvitedEvent(value.getTenant().getId(),
                value.getInvitedBy().getId(), value.getEmail(), value.getTenant().getName(), value.getRole().name(),
                token, value.getExpiresAt(), value.getId(), value.getStatus().name(), value.getCreatedAt()));
    }

    private void publishUser(User value) {
        LocalDateTime now = LocalDateTime.now();
        publish("tenant.user.projection.changed.v1", new UserProjectionChangedEvent(value.getId(),
                value.getTenant().getId(), value.getStatus().name(), value.getRole().name(),
                value.getCreatedAt() == null ? now : value.getCreatedAt(), now));
    }

    private void publishInvitationProjection(Invitation value) {
        LocalDateTime now = LocalDateTime.now();
        publish("tenant.invitation.projection.changed.v1", new InvitationProjectionChangedEvent(value.getId(),
                value.getTenant().getId(), value.getStatus().name(), value.getCreatedAt(), value.getExpiresAt(), now));
    }

    private void publish(String type, Object event) {
        if (durableEvents == null) events.publishEvent(event); else durableEvents.publish(type, 1, event);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static String token() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

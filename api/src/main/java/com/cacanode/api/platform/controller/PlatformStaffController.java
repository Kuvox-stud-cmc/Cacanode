package com.cacanode.api.platform.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.tenant.api.PlatformStaffApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/staff")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
public class PlatformStaffController extends BaseController {
    private final PlatformStaffApi staff;

    @GetMapping
    public PlatformStaffApi.PageResult<PlatformStaffApi.StaffItem> staff(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction, HttpServletRequest request) {
        return staff.staff(getTenantId(request), getUserId(request),
                new PlatformStaffApi.ListQuery(page, size, q, status, sort, direction));
    }

    @GetMapping("/invitations")
    public PlatformStaffApi.PageResult<PlatformStaffApi.InvitationItem> invitations(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction, HttpServletRequest request) {
        return staff.invitations(getTenantId(request), getUserId(request),
                new PlatformStaffApi.ListQuery(page, size, q, status, sort, direction));
    }

    @PostMapping("/invitations")
    public ResponseEntity<PlatformStaffApi.InvitationItem> invite(@Valid @RequestBody InviteRequest body,
                                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staff.invite(getTenantId(request),
                getUserId(request), body.email(), request.getRemoteAddr(), request.getHeader("User-Agent")));
    }

    @PostMapping("/invitations/{invitationId}/resend")
    public PlatformStaffApi.InvitationItem resend(@PathVariable UUID invitationId, HttpServletRequest request) {
        return staff.resend(getTenantId(request), getUserId(request), invitationId,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    @DeleteMapping("/invitations/{invitationId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID invitationId, HttpServletRequest request) {
        staff.cancel(getTenantId(request), getUserId(request), invitationId,
                request.getRemoteAddr(), request.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{staffId}/status")
    public PlatformStaffApi.StaffItem status(@PathVariable UUID staffId,
                                             @Valid @RequestBody StatusRequest body,
                                             HttpServletRequest request) {
        return staff.updateStatus(getTenantId(request), getUserId(request), staffId, body.status(),
                request.getRemoteAddr(), request.getHeader("User-Agent"));
    }

    public record InviteRequest(@NotNull @Email String email) {}
    public record StatusRequest(@NotNull String status) {}
}

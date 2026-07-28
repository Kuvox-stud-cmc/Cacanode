package com.cacanode.api.platform.controller;

import com.cacanode.api.recruitment.api.RecruitmentPlatformReadApi;
import com.cacanode.api.tenant.api.TenantKindApi;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@ConditionalOnProperty(prefix = "app.platform-administration", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "app.recruitment", name = "enabled", havingValue = "true")
public class PlatformJobController {
    private static final Map<String, RecruitmentPlatformReadApi.Sort> SORTS = Map.of(
            "title", RecruitmentPlatformReadApi.Sort.TITLE,
            "companyName", RecruitmentPlatformReadApi.Sort.COMPANY_NAME,
            "status", RecruitmentPlatformReadApi.Sort.STATUS,
            "publishedAt", RecruitmentPlatformReadApi.Sort.PUBLISHED_AT,
            "closingAt", RecruitmentPlatformReadApi.Sort.CLOSING_AT,
            "updatedAt", RecruitmentPlatformReadApi.Sort.UPDATED_AT,
            "applications", RecruitmentPlatformReadApi.Sort.APPLICATIONS,
            "interviews", RecruitmentPlatformReadApi.Sort.INTERVIEWS,
            "visibility", RecruitmentPlatformReadApi.Sort.VISIBILITY);

    private final RecruitmentPlatformReadApi recruitment;
    private final TenantKindApi tenantKinds;

    @GetMapping
    public RecruitmentPlatformReadApi.JobPage jobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) RecruitmentPlatformReadApi.JobStatus status,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) RecruitmentPlatformReadApi.EmploymentType employmentType,
            @RequestParam(required = false) RecruitmentPlatformReadApi.WorkMode workMode,
            @RequestParam(required = false) RecruitmentPlatformReadApi.Visibility visibility,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant closingFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant closingTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant updatedTo,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        validate(page, size, search, closingFrom, closingTo, updatedFrom, updatedTo);
        if (tenantId != null) requireCustomer(tenantId);
        RecruitmentPlatformReadApi.Sort safeSort = SORTS.get(sort);
        if (safeSort == null) throw badRequest("Unsupported job sort");
        RecruitmentPlatformReadApi.Direction safeDirection;
        try {
            safeDirection = RecruitmentPlatformReadApi.Direction.valueOf(direction.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw badRequest("direction must be asc or desc");
        }
        return recruitment.jobs(new RecruitmentPlatformReadApi.JobQuery(page, size, tenantId, status, search,
                language, department, location, employmentType, workMode, visibility, closingFrom, closingTo,
                updatedFrom, updatedTo, safeSort, safeDirection));
    }

    @GetMapping("/{jobId}")
    public RecruitmentPlatformReadApi.JobDetail job(@PathVariable UUID jobId) {
        RecruitmentPlatformReadApi.JobDetail job = recruitment.job(jobId);
        requireCustomer(job.tenantId());
        return job;
    }

    private static void validate(int page, int size, String search, Instant closingFrom, Instant closingTo,
                                 Instant updatedFrom, Instant updatedTo) {
        if (page < 0) throw badRequest("page must not be negative");
        if (size < 1 || size > 100) throw badRequest("size must be between 1 and 100");
        if (search != null && search.length() > 200) throw badRequest("q must not exceed 200 characters");
        if (closingFrom != null && closingTo != null && !closingFrom.isBefore(closingTo))
            throw badRequest("closingFrom must be before closingTo");
        if (updatedFrom != null && updatedTo != null && !updatedFrom.isBefore(updatedTo))
            throw badRequest("updatedFrom must be before updatedTo");
    }

    private void requireCustomer(UUID tenantId) {
        try {
            tenantKinds.requireCustomer(tenantId);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.query.PublicJobQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.cacanode.api.common.filter.TrustedProxyClientIpResolver;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicJobController {
    private final PublicJobQueryService jobs;
    private final com.cacanode.api.recruitment.query.PublicApplicationService applications;
    private final TrustedProxyClientIpResolver clientIps;

    @GetMapping("/jobs")
    public ResponseEntity<PublicRecruitmentDtos.PublicJobPage> jobs(
            @RequestParam(required=false,name="q") String query,
            @RequestParam(required=false) String department, @RequestParam(required=false) String location,
            @RequestParam(required=false) EmploymentType employmentType, @RequestParam(required=false) WorkMode workMode,
            @RequestParam(required=false) ExperienceLevel experienceLevel, @RequestParam(required=false) String language,
            @RequestParam(required=false) String sort, @RequestParam(required=false) String cursor,
            @RequestParam(required=false) Integer size) {
        return cache(jobs.search(new PublicJobQueryService.Search(query,null,department,location,
                employmentType,workMode,experienceLevel,language,sort,cursor,size)));
    }

    @GetMapping("/careers/{tenantSlug}/jobs")
    public ResponseEntity<PublicRecruitmentDtos.PublicJobPage> careers(
            @PathVariable String tenantSlug, @RequestParam(required=false,name="q") String query,
            @RequestParam(required=false) String department, @RequestParam(required=false) String location,
            @RequestParam(required=false) EmploymentType employmentType, @RequestParam(required=false) WorkMode workMode,
            @RequestParam(required=false) ExperienceLevel experienceLevel, @RequestParam(required=false) String language,
            @RequestParam(required=false) String sort, @RequestParam(required=false) String cursor,
            @RequestParam(required=false) Integer size) {
        return cache(jobs.search(new PublicJobQueryService.Search(query,tenantSlug,department,location,
                employmentType,workMode,experienceLevel,language,sort,cursor,size)));
    }

    @GetMapping("/jobs/{publicId}")
    public ResponseEntity<PublicRecruitmentDtos.PublicJob> detail(@PathVariable UUID publicId) {
        return cache(jobs.detail(publicId));
    }

    @PostMapping(value="/jobs/{publicId}/applications",consumes=org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PublicRecruitmentDtos.AcceptedApplication> apply(@PathVariable UUID publicId,
            @Valid @RequestPart("application") PublicRecruitmentDtos.ApplicationData application,
            @RequestPart(name="cv",required=false) MultipartFile cv,
            @RequestPart(name="turnstileToken",required=false) String turnstileToken,
            HttpServletRequest request){
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(
                applications.submit(publicId,application,cv,turnstileToken,clientIps.resolve(request)));
    }

    private static <T> ResponseEntity<T> cache(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic()).body(body);
    }
}

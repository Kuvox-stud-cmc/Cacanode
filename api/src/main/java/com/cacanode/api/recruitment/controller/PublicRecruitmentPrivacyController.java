package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.dto.RecruitmentPrivacyDtos;
import com.cacanode.api.recruitment.query.CandidateAccessService;
import com.cacanode.api.recruitment.service.RecruitmentPrivacyDeletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/applications")
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicRecruitmentPrivacyController {
    private final RecruitmentPrivacyDeletionService privacy;
    @PostMapping("/me/privacy-deletion-requests")
    public ResponseEntity<RecruitmentPrivacyDtos.Status> request(
            @CookieValue(name=CandidateAccessService.ACCESS_COOKIE,required=false) String access,
            @RequestHeader(name="X-CSRF-Token",required=false) String csrf){return noStore(privacy.requestByCandidate(access,csrf));}
    @PostMapping("/privacy-deletion-confirmations")
    public ResponseEntity<RecruitmentPrivacyDtos.Status> confirm(
            @Valid @RequestBody RecruitmentPrivacyDtos.Confirmation body){return noStore(privacy.confirm(body.token()));}
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}

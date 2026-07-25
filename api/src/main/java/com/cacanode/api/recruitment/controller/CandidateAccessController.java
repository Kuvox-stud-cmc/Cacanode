package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.query.CandidateAccessService;
import jakarta.servlet.http.HttpServletResponse;
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
public class CandidateAccessController {
    private final CandidateAccessService access;

    @PostMapping("/access")
    public ResponseEntity<PublicRecruitmentDtos.CandidateSessionResponse> exchange(
            @Valid @RequestBody PublicRecruitmentDtos.TokenExchange body,HttpServletResponse response){
        return noStore(access.exchange(body.token(),response));
    }
    @PostMapping("/session/refresh")
    public ResponseEntity<PublicRecruitmentDtos.CandidateSessionResponse> refresh(
            @CookieValue(name=CandidateAccessService.REFRESH_COOKIE,required=false) String refresh,HttpServletResponse response){
        return noStore(access.refresh(refresh,response));
    }
    @GetMapping("/me")
    public ResponseEntity<PublicRecruitmentDtos.CandidateApplication> me(
            @CookieValue(name=CandidateAccessService.ACCESS_COOKIE,required=false) String token){return noStore(access.me(token));}
    @PostMapping("/me/withdraw")
    public ResponseEntity<PublicRecruitmentDtos.CandidateApplication> withdraw(
            @CookieValue(name=CandidateAccessService.ACCESS_COOKIE,required=false) String token,
            @RequestHeader(name="X-CSRF-Token",required=false) String csrf){return noStore(access.withdraw(token,csrf));}
    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(@CookieValue(name=CandidateAccessService.ACCESS_COOKIE,required=false) String token,
            @RequestHeader(name="X-CSRF-Token",required=false) String csrf,HttpServletResponse response){
        access.logout(token,csrf,response);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private static <T> ResponseEntity<T> noStore(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}

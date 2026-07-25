package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.service.PublicInterviewSchedulingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/public/interview-invitations")
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicInterviewInvitationController {
    private final PublicInterviewSchedulingService scheduling;

    @PostMapping("/exchange") public ResponseEntity<PublicRecruitmentDtos.InvitationSessionResponse> exchange(
            @Valid @RequestBody PublicRecruitmentDtos.TokenExchange body,HttpServletResponse response){
        return secure(scheduling.exchange(body.token(),response));
    }
    @GetMapping("/me") public ResponseEntity<PublicRecruitmentDtos.InvitationDetails> details(
            @CookieValue(name=PublicInterviewSchedulingService.INVITATION_COOKIE,required=false) String token){return secure(scheduling.details(token));}
    @GetMapping("/me/slots") public ResponseEntity<PublicRecruitmentDtos.SlotPage> slots(
            @CookieValue(name=PublicInterviewSchedulingService.INVITATION_COOKIE,required=false) String token,
            @RequestParam(required=false) LocalDate from,@RequestParam(defaultValue="14") int days){return secure(scheduling.slots(token,from,days));}
    @PostMapping("/me/schedule") public ResponseEntity<PublicRecruitmentDtos.InvitationDetails> schedule(
            @CookieValue(name=PublicInterviewSchedulingService.INVITATION_COOKIE,required=false) String token,
            @RequestHeader(name="X-CSRF-Token",required=false) String csrf,
            @Valid @RequestBody PublicRecruitmentDtos.ScheduleRequest body){scheduling.requireCsrf(token,csrf);return secure(scheduling.schedule(token,body.startAt()));}
    @PostMapping("/me/reschedule") public ResponseEntity<PublicRecruitmentDtos.InvitationDetails> reschedule(
            @CookieValue(name=PublicInterviewSchedulingService.INVITATION_COOKIE,required=false) String token,
            @RequestHeader(name="X-CSRF-Token",required=false) String csrf,
            @Valid @RequestBody PublicRecruitmentDtos.ScheduleRequest body){scheduling.requireCsrf(token,csrf);return secure(scheduling.reschedule(token,body.startAt()));}
    @PostMapping("/me/withdraw") public ResponseEntity<PublicRecruitmentDtos.InvitationDetails> withdraw(
            @CookieValue(name=PublicInterviewSchedulingService.INVITATION_COOKIE,required=false) String token,
            @RequestHeader(name="X-CSRF-Token",required=false) String csrf){scheduling.requireCsrf(token,csrf);return secure(scheduling.withdraw(token));}
    @RequestMapping(value={"/{token}","/{token}/slots","/{token}/schedule","/{token}/reschedule","/{token}/withdraw"})
    public ResponseEntity<Void> legacyTokenPath(){return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").build();}
    private static <T> ResponseEntity<T> secure(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").body(body);}
}

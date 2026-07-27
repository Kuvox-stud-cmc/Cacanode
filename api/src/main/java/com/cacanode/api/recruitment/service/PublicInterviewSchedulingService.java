package com.cacanode.api.recruitment.service;

import com.cacanode.api.billing.api.HiringQuotaApi;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.common.exception.custom.UnauthorizedException;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.model.RecruitmentEnums.*;
import com.cacanode.api.recruitment.repository.*;
import com.cacanode.api.recruitment.query.RecruitmentInvitationQueryService;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicInterviewSchedulingService {
    public static final String INVITATION_COOKIE="recruitment_interview_invitation";
    private final RecruitmentInterviewInvitationTokenRepository tokens;
    private final RecruitmentInterviewRepository interviews;
    private final RecruitmentApplicationRepository applications;
    private final RecruitmentTenantSettingsRepository settings;
    private final RecruitmentAvailabilityWindowRepository windows;
    private final RecruitmentAvailabilityExceptionRepository exceptions;
    private final RecruitmentCandidateEmailDeliveryRepository deliveries;
    private final InterviewInvitationService invitationService;
    private final HiringQuotaApi quota;
    private final RecruitmentTokenSupport tokenSupport;
    private final RecruitmentInvitationQueryService queries;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RecruitmentInterviewCancellationService cancellations;
    private final PublicRecruitmentProperties publicProperties;
    @Autowired(required=false) private RecruitmentProjectionEventPublisher projectionEvents;

    @Transactional
    public PublicRecruitmentDtos.InvitationDetails details(String raw){return details(require(raw).interview());}

    @Transactional
    public PublicRecruitmentDtos.InvitationSessionResponse exchange(String raw,HttpServletResponse response) {
        PublicRecruitmentDtos.InvitationDetails invitation=details(raw);
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(INVITATION_COOKIE,raw).httpOnly(true)
                .secure(publicProperties.cookieSecure()).sameSite("Strict")
                .path("/api/v1/public/interview-invitations/me").maxAge(Duration.ofDays(7)).build().toString());
        return new PublicRecruitmentDtos.InvitationSessionResponse(csrf(raw),invitation);
    }

    public void requireCsrf(String raw,String supplied) {
        if(raw==null||supplied==null||!MessageDigest.isEqual(csrf(raw).getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)))throw unauthorized();
    }

    @Transactional
    public PublicRecruitmentDtos.SlotPage slots(String raw,LocalDate from,int days){
        if(days<1||days>14)throw new BadRequestException("days must be between 1 and 14");
        Access access=require(raw);RecruitmentTenantSettings s=tenantSettings(access.interview().getTenantId());
        ZoneId zone=ZoneId.of(s.getSchedulingTimezone());LocalDate today=Instant.now(clock).atZone(zone).toLocalDate();
        if(!windows.existsByTenantId(access.interview().getTenantId())
                && !exceptions.existsByTenantIdAndKindAndExceptionDateGreaterThanEqual(
                        access.interview().getTenantId(),AvailabilityExceptionKind.EXTRA,today))
            throw new ConflictException("INTERVIEW_AVAILABILITY_NOT_CONFIGURED");
        LocalDate start=from==null?today:from;if(start.isBefore(today))start=today;
        LocalDate horizon=today.plusDays(s.getBookingHorizonDays());
        if(start.isAfter(horizon))return new PublicRecruitmentDtos.SlotPage(List.of(),null,zone.getId());
        LocalDate end=start.plusDays(days);LocalDate limit=horizon.plusDays(1);if(end.isAfter(limit))end=limit;
        List<PublicRecruitmentDtos.InterviewSlot> result=generate(access.interview(),s,start,end,false);
        return new PublicRecruitmentDtos.SlotPage(result,end.isBefore(limit)?end:null,zone.getId());
    }

    @Transactional
    public PublicRecruitmentDtos.InvitationDetails schedule(String raw,Instant startAt){return book(raw,startAt,false);}

    @Transactional
    public PublicRecruitmentDtos.InvitationDetails reschedule(String raw,Instant startAt){return book(raw,startAt,true);}

    @Transactional
    public PublicRecruitmentDtos.InvitationDetails withdraw(String raw){Access access=require(raw);cancellations.withdraw(access.interview().getTenantId(),access.interview().getApplicationId());return details(interviews.findByIdAndTenantId(access.interview().getId(),access.interview().getTenantId()).orElseThrow(PublicInterviewSchedulingService::unauthorized));}

    private PublicRecruitmentDtos.InvitationDetails book(String raw,Instant startAt,boolean reschedule){
        Access access=require(raw);RecruitmentInterview interview=interviews.findForUpdate(access.interview().getTenantId(),access.interview().getId()).orElseThrow(PublicInterviewSchedulingService::unauthorized);
        if(interview.getStatus()==InterviewStatus.SCHEDULED && Objects.equals(interview.getScheduledStartAt(),startAt))return details(interview);
        LocalDateTime now=LocalDateTime.now(clock);RecruitmentTenantSettings s=tenantSettings(interview.getTenantId());
        if(reschedule){
            if(interview.getStatus()!=InterviewStatus.SCHEDULED)throw new ConflictException("Interview is not scheduled");
            if(!Instant.now(clock).plus(Duration.ofMinutes(s.getRescheduleCutoffMinutes())).isBefore(interview.getScheduledStartAt()))
                throw new ConflictException("RESCHEDULE_CUTOFF");
        }else if(interview.getStatus()!=InterviewStatus.INVITED)throw new ConflictException("Interview cannot be scheduled");
        ZoneId zone=ZoneId.of(s.getSchedulingTimezone());LocalDate date=startAt.atZone(zone).toLocalDate();
        boolean available=generate(interview,s,date,date.plusDays(1),true).stream().anyMatch(slot->slot.startAt().equals(startAt));
        if(!available)throw new ConflictException("SLOT_UNAVAILABLE");
        long exactSeconds=durationSeconds(interview);long bookingSeconds=((exactSeconds+899)/900)*900;
        Instant endAt=startAt.plusSeconds(bookingSeconds);LocalDateTime reservationExpiry=LocalDateTime.ofInstant(endAt.plus(Duration.ofHours(24)),ZoneOffset.UTC);
        try {
            if(interview.getQuotaReservationId()==null){
                HiringQuotaApi.Reservation reservation=quota.reserveInterviewSeconds(interview.getTenantId(),interview.getId(),exactSeconds,reservationExpiry);
                interview.setQuotaReservationId(reservation.reservationId());interview.setQuotaReservedSeconds(exactSeconds);
            }else quota.updateInterviewReservationExpiry(interview.getTenantId(),interview.getQuotaReservationId(),reservationExpiry);
        }catch(HiringQuotaApi.HiringQuotaException e){throw new ConflictException("INTERVIEW_QUOTA_EXHAUSTED");}
        interview.setQuotaReservationExpiresAt(reservationExpiry);interview.setScheduledStartAt(startAt);interview.setScheduledEndAt(endAt);
        interview.setScheduledAt(LocalDateTime.ofInstant(startAt,ZoneOffset.UTC));interview.setSchedulingTimezone(zone.getId());
        interview.setStatus(InterviewStatus.SCHEDULED);interview.setScheduleVersion(interview.getScheduleVersion()+1);
        if(reschedule)interview.setRescheduleCount(interview.getRescheduleCount()+1);
        try{interviews.saveAndFlush(interview);}catch(DataIntegrityViolationException e){throw new ConflictException("SLOT_UNAVAILABLE");}
        RecruitmentApplication application=applications.findForUpdate(interview.getTenantId(),interview.getApplicationId()).orElseThrow(PublicInterviewSchedulingService::unauthorized);
        application.setStatus(ApplicationStatus.INTERVIEW_SCHEDULED);application=applications.save(application);
        if(projectionEvents!=null){projectionEvents.interview(interview,reschedule?"interview.rescheduled":"interview.scheduled");projectionEvents.application(application,null);}
        deliveries.cancelReminders(interview.getId(),CandidateEmailState.CANCELLED,now);
        CandidateEmailKind confirmation=reschedule?CandidateEmailKind.RESCHEDULE_CONFIRMATION:CandidateEmailKind.CONFIRMATION;
        invitationService.enqueue(interview,confirmation,confirmation.name().toLowerCase()+"-v"+interview.getScheduleVersion(),now);
        for(Integer offset:s.getReminderOffsetsMinutes()){
            LocalDateTime due=LocalDateTime.ofInstant(startAt.minus(Duration.ofMinutes(offset)),ZoneOffset.UTC);
            if(due.isAfter(now))invitationService.enqueue(interview,CandidateEmailKind.REMINDER,
                    "reminder-v"+interview.getScheduleVersion()+"-"+offset,due);
        }
        return details(interview);
    }

    private List<PublicRecruitmentDtos.InterviewSlot> generate(RecruitmentInterview interview,RecruitmentTenantSettings s,
            LocalDate from,LocalDate to,boolean ignoreCurrent){
        ZoneId zone=ZoneId.of(s.getSchedulingTimezone());Instant now=Instant.now(clock);
        Instant earliest=now.plus(Duration.ofMinutes(s.getMinimumNoticeMinutes()));Instant horizon=now.plus(Duration.ofDays(s.getBookingHorizonDays()));
        long exact=durationSeconds(interview),booking=((exact+899)/900)*900;
        Map<Integer,List<Range>> weekly=new HashMap<>();for(var w:windows.findByTenantIdOrderByDayOfWeekAscStartLocalAsc(interview.getTenantId()))weekly.computeIfAbsent(w.getDayOfWeek(),x->new ArrayList<>()).add(new Range(w.getStartLocal(),w.getEndLocal()));
        Map<LocalDate,List<RecruitmentAvailabilityException>> dated=new HashMap<>();if(from.isBefore(to))for(var e:exceptions.findByTenantIdAndExceptionDateBetweenOrderByExceptionDateAscStartLocalAsc(interview.getTenantId(),from,to.minusDays(1)))dated.computeIfAbsent(e.getExceptionDate(),x->new ArrayList<>()).add(e);
        List<PublicRecruitmentDtos.InterviewSlot> slots=new ArrayList<>();
        for(LocalDate date=from;date.isBefore(to);date=date.plusDays(1)){
            List<Range> ranges=new ArrayList<>(weekly.getOrDefault(date.getDayOfWeek().getValue(),List.of()));
            for(var e:dated.getOrDefault(date,List.of()))if(e.getKind()==AvailabilityExceptionKind.EXTRA)ranges.add(new Range(e.getStartLocal(),e.getEndLocal()));
            for(var e:dated.getOrDefault(date,List.of()))if(e.getKind()==AvailabilityExceptionKind.BLACKOUT)ranges=subtract(ranges,new Range(e.getStartLocal(),e.getEndLocal()));
            for(Range range:ranges){LocalDateTime local=align(LocalDateTime.of(date,range.start()),s.getSlotGridMinutes());LocalDateTime localEnd=LocalDateTime.of(date,range.end());
                while(!local.plusSeconds(booking).isAfter(localEnd)){
                    for(Instant start:resolveLocal(local,zone)){Instant end=start.plusSeconds(booking);
                        if(!start.isBefore(earliest)&&!start.isAfter(horizon)&&interviews.findOverlapping(interview.getTenantId(),start,end).stream().noneMatch(i->ignoreCurrent&&i.getId().equals(interview.getId())?false:true))
                            slots.add(new PublicRecruitmentDtos.InterviewSlot(start,end,zone.getId()));}
                    local=local.plusMinutes(s.getSlotGridMinutes());}
            }
        }
        return slots.stream().distinct().sorted(Comparator.comparing(PublicRecruitmentDtos.InterviewSlot::startAt)).toList();
    }

    private Access require(String raw){
        if(raw==null||raw.isBlank())throw unauthorized();RecruitmentInterviewInvitationToken token=tokens.findForUpdateByHash(tokenSupport.hash(raw)).orElseThrow(PublicInterviewSchedulingService::unauthorized);
        LocalDateTime now=LocalDateTime.now(clock);if(!token.getExpiresAt().isAfter(now))throw expired();if(token.getRevokedAt()!=null)throw unauthorized();
        RecruitmentInterview interview=interviews.findByIdAndTenantId(token.getInterviewId(),token.getTenantId()).orElseThrow(PublicInterviewSchedulingService::unauthorized);
        if(!interview.getApplicationId().equals(token.getApplicationId())||interview.getStatus()==InterviewStatus.CANCELLED)throw unauthorized();
        if(interview.getStatus()==InterviewStatus.EXPIRED || interview.getStatus()==InterviewStatus.INVITED&&(interview.getInvitationExpiresAt()==null||!interview.getInvitationExpiresAt().isAfter(now)))throw expired();
        return new Access(token,interview);
    }
    private RecruitmentTenantSettings tenantSettings(UUID tenantId){return settings.findById(tenantId).orElseGet(()->{var s=new RecruitmentTenantSettings();s.setTenantId(tenantId);return s;});}
    private long durationSeconds(RecruitmentInterview interview){try{return objectMapper.readTree(interview.getTemplateSnapshot()).path("durationLimitSeconds").asLong();}catch(Exception e){throw new IllegalStateException("Interview snapshot is invalid",e);}}
    private PublicRecruitmentDtos.InvitationDetails details(RecruitmentInterview interview){return queries.details(interview);}
    private static LocalDateTime align(LocalDateTime value,int grid){int minute=value.getHour()*60+value.getMinute();int aligned=((minute+grid-1)/grid)*grid;return value.toLocalDate().atStartOfDay().plusMinutes(aligned);}
    static List<Instant> resolveLocal(LocalDateTime local,ZoneId zone){return zone.getRules().getValidOffsets(local).stream().map(offset->ZonedDateTime.ofLocal(local,zone,offset).toInstant()).toList();}
    private static List<Range> subtract(List<Range> input,Range cut){List<Range> out=new ArrayList<>();for(Range r:input){if(!r.start().isBefore(cut.end())||!cut.start().isBefore(r.end()))out.add(r);else{if(r.start().isBefore(cut.start()))out.add(new Range(r.start(),cut.start()));if(cut.end().isBefore(r.end()))out.add(new Range(cut.end(),r.end()));}}return out;}
    private static UnauthorizedException unauthorized(){return new UnauthorizedException("Invalid or expired interview invitation");}
    private String csrf(String raw){return tokenSupport.hash("interview-invitation-csrf:"+raw);}
    private static UnauthorizedException expired(){return new UnauthorizedException("INVITATION_EXPIRED");}
    private record Range(LocalTime start,LocalTime end){}
    private record Access(RecruitmentInterviewInvitationToken token,RecruitmentInterview interview){}
}

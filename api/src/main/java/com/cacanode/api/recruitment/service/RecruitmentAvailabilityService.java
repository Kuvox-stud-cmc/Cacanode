package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.cacanode.api.recruitment.model.*;
import com.cacanode.api.recruitment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentAvailabilityService {
    private final RecruitmentTenantSettingsRepository settings;
    private final RecruitmentAvailabilityWindowRepository windows;
    private final RecruitmentAvailabilityExceptionRepository exceptions;

    @Transactional(readOnly=true)
    public RecruitmentDtos.AvailabilityResponse get(UUID tenantId){
        RecruitmentTenantSettings s=settings.findById(tenantId).orElse(null);
        String zone=s==null?"Asia/Ho_Chi_Minh":s.getSchedulingTimezone();long version=s==null?0:s.getVersion();
        return new RecruitmentDtos.AvailabilityResponse(zone,
                windows.findByTenantIdOrderByDayOfWeekAscStartLocalAsc(tenantId).stream()
                        .map(w->new RecruitmentDtos.AvailabilityWindow(w.getDayOfWeek(),w.getStartLocal(),w.getEndLocal())).toList(),
                exceptions.findByTenantIdOrderByExceptionDateAscStartLocalAsc(tenantId).stream()
                        .map(e->new RecruitmentDtos.AvailabilityException(e.getExceptionDate(),e.getKind(),e.getStartLocal(),e.getEndLocal())).toList(),version);
    }

    @Transactional
    public RecruitmentDtos.AvailabilityResponse replace(UUID tenantId,RecruitmentDtos.AvailabilityUpdate request){
        RecruitmentTenantSettings s=settings.findForUpdate(tenantId).orElse(null);
        long current=s==null?0:s.getVersion();if(current!=request.version())throw new ConflictException("Availability version is stale");
        if(s==null){s=new RecruitmentTenantSettings();s.setTenantId(tenantId);settings.saveAndFlush(s);}
        ZoneId.of(s.getSchedulingTimezone());validate(request);
        windows.deleteByTenantId(tenantId);exceptions.deleteByTenantId(tenantId);windows.flush();exceptions.flush();
        for(var item:request.weeklyWindows()){var w=new RecruitmentAvailabilityWindow();w.setTenantId(tenantId);
            w.setDayOfWeek(item.dayOfWeek());w.setStartLocal(item.startLocal());w.setEndLocal(item.endLocal());windows.save(w);}
        for(var item:request.exceptions()){var e=new RecruitmentAvailabilityException();e.setTenantId(tenantId);
            e.setExceptionDate(item.date());e.setKind(item.kind());e.setStartLocal(item.startLocal());e.setEndLocal(item.endLocal());exceptions.save(e);}
        s.setUpdatedAt(LocalDateTime.now());settings.saveAndFlush(s);windows.flush();exceptions.flush();return get(tenantId);
    }

    private static void validate(RecruitmentDtos.AvailabilityUpdate request){
        Map<Integer,List<Range>> weekly=new HashMap<>();for(var w:request.weeklyWindows()){
            if(w.startLocal()==null||w.endLocal()==null||!w.startLocal().isBefore(w.endLocal()))throw new BadRequestException("Availability windows must be same-day ranges");
            weekly.computeIfAbsent(w.dayOfWeek(),x->new ArrayList<>()).add(new Range(w.startLocal(),w.endLocal()));}
        weekly.values().forEach(RecruitmentAvailabilityService::noOverlap);
        Map<String,List<Range>> dated=new HashMap<>();for(var e:request.exceptions()){
            if(e.startLocal()==null||e.endLocal()==null||!e.startLocal().isBefore(e.endLocal()))throw new BadRequestException("Availability exceptions must be same-day ranges");
            dated.computeIfAbsent(e.date()+":"+e.kind(),x->new ArrayList<>()).add(new Range(e.startLocal(),e.endLocal()));}
        dated.values().forEach(RecruitmentAvailabilityService::noOverlap);
    }
    private static void noOverlap(List<Range> ranges){ranges.sort(Comparator.comparing(Range::start));for(int i=1;i<ranges.size();i++)if(ranges.get(i).start().isBefore(ranges.get(i-1).end()))throw new BadRequestException("Availability ranges cannot overlap");}
    private record Range(java.time.LocalTime start,java.time.LocalTime end){}
}

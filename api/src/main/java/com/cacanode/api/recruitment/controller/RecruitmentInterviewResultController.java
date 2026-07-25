package com.cacanode.api.recruitment.controller;

import com.cacanode.api.common.controller.BaseController;
import com.cacanode.api.recruitment.dto.InterviewResultDtos;
import com.cacanode.api.recruitment.query.RecruitmentInterviewResultQueryService;
import com.cacanode.api.common.storage.DocumentStorage;
import com.cacanode.api.common.enums.LogAction;
import com.cacanode.api.common.event.AuditLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/recruitment/interviews/{interviewId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','TENANT_ADMIN')")
@ConditionalOnProperty(prefix="app.recruitment",name="enabled",havingValue="true")
public class RecruitmentInterviewResultController extends BaseController {
    private final RecruitmentInterviewResultQueryService results;
    private final DocumentStorage storage;
    private final ApplicationEventPublisher events;

    @GetMapping("/transcript")
    public ResponseEntity<InterviewResultDtos.Transcript> transcript(@PathVariable UUID interviewId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(results.transcript(getTenantId(request),interviewId,page,size));
    }

    @GetMapping("/result")
    public ResponseEntity<InterviewResultDtos.Result> result(@PathVariable UUID interviewId,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(results.result(getTenantId(request),interviewId));
    }

    @GetMapping("/recordings")
    public ResponseEntity<List<InterviewResultDtos.Recording>> recordings(@PathVariable UUID interviewId,HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(results.recordings(getTenantId(request),interviewId));
    }

    @GetMapping("/recordings/{recordingId}/playback")
    public ResponseEntity<byte[]> playback(@PathVariable UUID interviewId,@PathVariable UUID recordingId,
            @RequestHeader(value=HttpHeaders.RANGE,required=false) String range,HttpServletRequest request) {
        UUID tenantId=getTenantId(request);var recording=results.recording(tenantId,interviewId,recordingId);
        long[] bounds=range(range,recording.sizeBytes());var content=storage.loadRange(recording.storageKey(),bounds[0],bounds[1]);
        audit(tenantId,getUserId(request),interviewId,LogAction.RECRUITMENT_RECORDING_PLAYED,request);
        ResponseEntity.BodyBuilder response=ResponseEntity.status(range==null?HttpStatus.OK:HttpStatus.PARTIAL_CONTENT)
                .cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(recording.contentType()))
                .header(HttpHeaders.ACCEPT_RANGES,"bytes").contentLength(content.content().length);
        if(range!=null)response.header(HttpHeaders.CONTENT_RANGE,"bytes "+bounds[0]+"-"+bounds[1]+"/"+recording.sizeBytes());
        return response.body(content.content());
    }

    @GetMapping("/recordings/{recordingId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID interviewId,@PathVariable UUID recordingId,HttpServletRequest request) {
        UUID tenantId=getTenantId(request);var recording=results.recording(tenantId,interviewId,recordingId);
        var content=storage.load(recording.storageKey());audit(tenantId,getUserId(request),interviewId,
                LogAction.RECRUITMENT_RECORDING_DOWNLOADED,request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(recording.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=interview-recording.mp3")
                .contentLength(content.content().length).body(content.content());
    }

    private static long[] range(String value,long size){if(size<=0)throw new IllegalArgumentException("Empty recording");if(value==null)return new long[]{0,size-1};
        if(!value.startsWith("bytes=")||value.contains(","))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        String[] parts=value.substring(6).split("-",-1);try{long start=parts[0].isBlank()?Math.max(0,size-Long.parseLong(parts[1])):Long.parseLong(parts[0]);
            long end=parts[1].isBlank()?size-1:Math.min(size-1,Long.parseLong(parts[1]));if(start<0||start>=size||end<start)throw new NumberFormatException();return new long[]{start,end};}
        catch(RuntimeException exception){throw new org.springframework.web.server.ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);}}
    private void audit(UUID tenantId,UUID userId,UUID interviewId,LogAction action,HttpServletRequest request){events.publishEvent(
            AuditLogEvent.builder(this).tenantId(tenantId).userId(userId).action(action).resourceType("recruitment_interview")
                    .resourceId(interviewId).ipAddress(request.getRemoteAddr()).userAgent(request.getHeader("User-Agent"))
                    .metadata(Map.of("access","recording")).build());}
}

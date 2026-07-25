package com.cacanode.api.recruitment.controller;

import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.cacanode.api.recruitment.model.RecruitmentEnums.TwilioCallbackKind;
import com.cacanode.api.recruitment.service.TwilioCallbackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/twilio/interviews")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class TwilioInterviewCallbackController {
    private final TwilioCallbackService callbacks;
    private final RecruitmentCallingProperties properties;

    @PostMapping(value="/voice",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> voice(@RequestParam UUID attempt,
            @RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        callbacks.validate(request,form);var call=callbacks.bind(attempt,form,TwilioCallbackKind.VOICE,"voice");
        if(callbacks.isTerminal(call))return xml("<Response><Hangup/></Response>");
        String action=url("consent",attempt,"round=1");
        return xml("<Response><Gather input=\"dtmf\" numDigits=\"1\" timeout=\"8\" actionOnEmptyResult=\"true\" method=\"POST\" action=\""+
                escape(action)+"\"><Say voice=\""+escape(voice(call))+"\" language=\""+language(call)+"\">"+
                escape(callbacks.disclosure(call))+"</Say></Gather></Response>");
    }

    @PostMapping(value="/consent",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> consent(@RequestParam UUID attempt,@RequestParam int round,
            @RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        callbacks.validate(request,form);var call=callbacks.bind(attempt,form,TwilioCallbackKind.CONSENT,"consent:"+round);
        if(callbacks.isTerminal(call))return xml("<Response><Hangup/></Response>");
        String digits=form.getFirst("Digits");
        if("1".equals(digits)) {
            callbacks.consent(call,true,null);
            return xml("<Response><Connect><Stream url=\""+escape(properties.mediaStreamWssUrl())+
                    "\" statusCallback=\""+escape(url("stream-status",attempt,null))+"\" statusCallbackMethod=\"POST\">"+
                    "<Parameter name=\"token\" value=\""+escape(callbacks.runtimeToken(call))+"\"/></Stream></Connect></Response>");
        }
        if("2".equals(digits)) {callbacks.consent(call,false,"CONSENT_DECLINED");return xml("<Response><Hangup/></Response>");}
        if(round<2) {
            String action=url("consent",attempt,"round=2");
            String retry=language(call).equals("vi-VN")?"Vui lòng nhấn phím 1 để đồng ý hoặc phím 2 để từ chối.":
                    "Please press 1 to consent or 2 to decline.";
            return xml("<Response><Gather input=\"dtmf\" numDigits=\"1\" timeout=\"8\" actionOnEmptyResult=\"true\" method=\"POST\" action=\""+
                    escape(action)+"\"><Say voice=\""+escape(voice(call))+"\" language=\""+language(call)+"\">"+
                    escape(retry)+"</Say></Gather></Response>");
        }
        callbacks.consent(call,false,"CONSENT_NOT_RECEIVED");return xml("<Response><Hangup/></Response>");
    }

    @PostMapping(value="/status",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(@RequestParam UUID attempt,@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        callbacks.validate(request,form);callbacks.status(attempt,form);return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/stream-status",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> streamStatus(@RequestParam UUID attempt,@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        callbacks.validate(request,form);callbacks.streamStatus(attempt,form);return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/recording-status",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> recordingStatus(@RequestParam UUID attempt,@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        callbacks.validate(request,form);String sid=form.getFirst("RecordingSid");var call=callbacks.bind(attempt,form,
                TwilioCallbackKind.RECORDING_STATUS,"recording:"+(sid==null?"missing":sid));
        callbacks.recordingStatus(call,form);return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/fallback",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> fallback(@RequestParam UUID attempt,@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        callbacks.validate(request,form);callbacks.fallback(attempt,form);return xml("<Response><Hangup/></Response>");
    }

    private String url(String endpoint,UUID attempt,String extra) {
        String value=properties.callbackBaseUrl().replaceAll("/+$","")+"/api/v1/public/twilio/interviews/"+endpoint+"?attempt="+attempt;
        if(extra!=null)value+="&"+extra;return value;
    }
    private String language(com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt call){return callbacks.disclosure(call).startsWith("Đ")?"vi-VN":"en-US";}
    private String voice(com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt call){return "vi-VN".equals(language(call))?
            properties.consentVoiceVietnamese():properties.consentVoiceEnglish();}
    private static ResponseEntity<String> xml(String value){return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(value);}
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("\"","&quot;").replace("'","&apos;");}
}

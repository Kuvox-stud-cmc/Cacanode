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
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/twilio/interviews")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class TwilioInterviewCallbackController {
    private final TwilioCallbackService callbacks;
    private final RecruitmentCallingProperties properties;

    @PostMapping(value="/voice",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> voice(@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        UUID attempt=attempt(request);
        callbacks.validate(request,form);var call=callbacks.bind(attempt,form,TwilioCallbackKind.VOICE,"voice");
        if(callbacks.isTerminal(call))return xml("<Response><Hangup/></Response>");
        String action=url("consent",attempt,"round=1");
        String instruction="vi-VN".equals(language(call))
                ?" Bạn cũng có thể nói đồng ý để tiếp tục hoặc nói từ chối để kết thúc. Hãy trả lời ngay bây giờ."
                :" You may also say yes to consent or no to decline. Please answer now.";
        return xml(gatherConsent(call,action,callbacks.disclosure(call)+instruction));
    }

    @PostMapping(value="/consent",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> consent(@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        UUID attempt=attempt(request);int round=round(request);
        callbacks.validate(request,form);var call=callbacks.bind(attempt,form,TwilioCallbackKind.CONSENT,"consent:"+round);
        if(callbacks.isTerminal(call))return xml("<Response><Hangup/></Response>");
        String digits=consentDigit(form.getFirst("Digits"));
        String speech=normalizedSpeech(form.getFirst("SpeechResult"));
        boolean rejected="2".equals(digits)||negativeSpeech(speech);
        boolean accepted=!rejected&&("1".equals(digits)||affirmativeSpeech(speech));
        if(accepted) {
            callbacks.consent(call,true,null);
            return xml("<Response><Connect><Stream url=\""+escape(properties.mediaStreamWssUrl())+
                    "\" statusCallback=\""+escape(url("stream-status",attempt,null))+"\" statusCallbackMethod=\"POST\">"+
                    "<Parameter name=\"token\" value=\""+escape(callbacks.runtimeToken(call))+"\"/></Stream></Connect></Response>");
        }
        if(rejected) {callbacks.consent(call,false,"CONSENT_DECLINED");return xml("<Response><Hangup/></Response>");}
        if(round<4) {
            int nextRound=round+1;String action=url("consent",attempt,"round="+nextRound);
            return xml(gatherConsent(call,action,retryInstruction(language(call))));
        }
        callbacks.consent(call,false,"CONSENT_NOT_RECEIVED");return xml("<Response><Hangup/></Response>");
    }

    @PostMapping(value="/status",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> status(@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        UUID attempt=attempt(request);
        callbacks.validate(request,form);callbacks.status(attempt,form);return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/stream-status",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> streamStatus(@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        UUID attempt=attempt(request);
        callbacks.validate(request,form);callbacks.streamStatus(attempt,form);return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/recording-status",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> recordingStatus(@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        UUID attempt=attempt(request);
        callbacks.validate(request,form);String sid=form.getFirst("RecordingSid");var call=callbacks.bind(attempt,form,
                TwilioCallbackKind.RECORDING_STATUS,"recording:"+(sid==null?"missing":sid));
        callbacks.recordingStatus(call,form);return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/fallback",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE,produces=MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> fallback(@RequestBody MultiValueMap<String,String> form,HttpServletRequest request) {
        UUID attempt=attempt(request);
        callbacks.validate(request,form);callbacks.fallback(attempt,form);return xml("<Response><Hangup/></Response>");
    }

    private String url(String endpoint,UUID attempt,String extra) {
        String value=properties.callbackBaseUrl().replaceAll("/+$","")+"/api/v1/public/twilio/interviews/"+endpoint+"?attempt="+attempt;
        if(extra!=null)value+="&"+extra;return value;
    }
    private String language(com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt call){return callbacks.languageTag(call);}
    private String voice(com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt call){return "vi-VN".equals(language(call))?
            properties.consentVoiceVietnamese():properties.consentVoiceEnglish();}
    private String gatherConsent(com.cacanode.api.recruitment.model.RecruitmentInterviewCallAttempt call,String action,
            String text) {
        String language=language(call);
        return "<Response><Gather input=\"dtmf speech\" numDigits=\"1\" timeout=\"6\" speechTimeout=\"1\" "+
                "speechModel=\"experimental_utterances\" language=\""+language+"\" hints=\""+
                escape(speechHints(language))+"\" actionOnEmptyResult=\"true\" method=\"POST\" action=\""+
                escape(action)+"\"><Say voice=\""+escape(voice(call))+"\" language=\""+language+"\">"+
                escape(text)+"</Say></Gather></Response>";
    }
    private static String retryInstruction(String language) {
        return "vi-VN".equals(language)
                ?"Hãy trả lời ngay. Nhấn phím 1 hoặc nói đồng ý. Nhấn phím 2 hoặc nói từ chối."
                :"Please answer now. Press 1 or say yes. Press 2 or say no.";
    }
    private static String speechHints(String language) {
        return "vi-VN".equals(language)
                ?"đồng ý, tôi đồng ý, vâng, có, một, từ chối, không, hai"
                :"yes, yes yes, agree, I agree, I consent, one, no, decline, I decline, two";
    }
    private static String normalizedSpeech(String value) {
        if(value==null)return "";
        return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    private static String consentDigit(String value) {
        if(value==null)return "";
        for(int index=0;index<value.length();index++) {
            char digit=value.charAt(index);if(digit=='1'||digit=='2')return String.valueOf(digit);
        }
        return "";
    }
    private static boolean affirmativeSpeech(String value) {
        return containsPhrase(value,Set.of("đồng ý","tôi đồng ý"))||containsToken(value,
                Set.of("yes","yeah","yep","sure","okay","ok","one","agree","consent",
                        "vâng","có","một"));
    }
    private static boolean negativeSpeech(String value) {
        return containsPhrase(value,Set.of("do not","don t","did not","can not","từ chối"))
                ||containsToken(value,Set.of("no","nope","two","decline","declined","reject","refuse",
                        "not","không","hai"));
    }
    private static boolean containsToken(String value,Set<String> expected) {
        if(value==null||value.isBlank())return false;
        for(String token:value.split(" "))if(expected.contains(token))return true;
        return false;
    }
    private static boolean containsPhrase(String value,Set<String> expected) {
        if(value==null||value.isBlank())return false;
        String padded=" "+value+" ";
        for(String phrase:expected)if(padded.contains(" "+phrase+" "))return true;
        return false;
    }
    private static UUID attempt(HttpServletRequest request) {
        try{return UUID.fromString(queryValue(request,"attempt"));}
        catch(IllegalArgumentException exception){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_ATTEMPT");}
    }
    private static int round(HttpServletRequest request) {
        try{return Integer.parseInt(queryValue(request,"round"));}
        catch(IllegalArgumentException exception){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_ROUND");}
    }
    private static String queryValue(HttpServletRequest request,String name) {
        String query=request.getQueryString();
        if(query!=null)for(String part:query.split("&")) {
            int separator=part.indexOf('=');
            String key=URLDecoder.decode(separator<0?part:part.substring(0,separator),StandardCharsets.UTF_8);
            if(key.equals(name))return URLDecoder.decode(separator<0?"":part.substring(separator+1),StandardCharsets.UTF_8);
        }
        throw new IllegalArgumentException("Missing query parameter");
    }
    private static ResponseEntity<String> xml(String value){return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(value);}
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
            .replace("\"","&quot;").replace("'","&apos;");}
}

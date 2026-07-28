package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix="app.recruitment",name="recording-enabled",havingValue="true")
public class TwilioRecordingTransport implements RecordingTransport {
    private final RecruitmentCallingProperties properties;private final ObjectMapper mapper;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public TwilioRecordingTransport(RecruitmentCallingProperties properties,ObjectMapper mapper){this.properties=properties;this.mapper=mapper;}

    @Override public Recording startDualChannelMp3(String callSid,String callback) {
        String form="RecordingChannels=dual&RecordingTrack=both&Trim=do-not-trim&RecordingStatusCallbackEvent=completed&RecordingStatusCallback="+encode(callback);
        JsonNode value=json(send(HttpRequest.newBuilder(uri("/Calls/"+callSid+"/Recordings.json"))
                .header("Authorization",authorization()).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString()));
        return recording(value);
    }
    @Override public Optional<Recording> findForCall(String callSid) {
        JsonNode value=json(send(HttpRequest.newBuilder(uri("/Calls/"+callSid+"/Recordings.json?PageSize=20"))
                .header("Authorization",authorization()).GET().build(),HttpResponse.BodyHandlers.ofString()));
        JsonNode items=value.path("recordings");if(!items.isArray()||items.isEmpty())return Optional.empty();return Optional.of(recording(items.get(0)));
    }
    @Override public void stop(String recordingSid) {
        send(HttpRequest.newBuilder(uri("/Recordings/"+recordingSid+".json"))
                .header("Authorization",authorization()).header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("Status=stopped")).build(),HttpResponse.BodyHandlers.discarding());
    }
    @Override public long downloadMp3(String recordingSid,OutputStream target,long maximumBytes) {
        HttpResponse<InputStream> response=send(HttpRequest.newBuilder(uri("/Recordings/"+recordingSid+".mp3"))
                .header("Authorization",authorization()).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(InputStream input=response.body()){byte[] buffer=new byte[64*1024];long total=0;int read;while((read=input.read(buffer))>=0){total+=read;if(total>maximumBytes)throw new DefiniteFailure("TWILIO_RECORDING_TOO_LARGE",null);target.write(buffer,0,read);}return total;}
        catch(DefiniteFailure failure){throw failure;}catch(Exception exception){throw new UncertainFailure("TWILIO_RECORDING_DOWNLOAD_FAILED",exception);}
    }
    @Override public void delete(String recordingSid) {send(HttpRequest.newBuilder(uri("/Recordings/"+recordingSid+".json"))
            .header("Authorization",authorization()).DELETE().build(),HttpResponse.BodyHandlers.discarding());}
    @Override public boolean exists(String recordingSid) {try{send(HttpRequest.newBuilder(uri("/Recordings/"+recordingSid+".json"))
            .header("Authorization",authorization()).GET().build(),HttpResponse.BodyHandlers.discarding());return true;}
        catch(DefiniteFailure exception){return !exception.getMessage().contains("404");}}
    private URI uri(String suffix){return URI.create("https://api.twilio.com/2010-04-01/Accounts/"+properties.twilioAccountSid()+suffix);}
    private String authorization(){String value=properties.twilioApiKeySid()+":"+properties.twilioApiKeySecret();return "Basic "+Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));}
    private JsonNode json(HttpResponse<String> response){try{return mapper.readTree(response.body());}catch(Exception exception){throw new UncertainFailure("TWILIO_RECORDING_RESPONSE_INVALID",exception);}}
    private Recording recording(JsonNode value){String sid=value.path("sid").asText();if(!sid.matches("^RE[0-9a-fA-F]{32}$"))throw new DefiniteFailure("TWILIO_RECORDING_SID_INVALID",null);return new Recording(sid,value.path("status").asText());}
    private <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler){try{HttpResponse<T> response=client.send(request,handler);if(response.statusCode()>=200&&response.statusCode()<300)return response;
            if(response.statusCode()==404)throw new DefiniteFailure("TWILIO_RECORDING_404",null);if(response.statusCode()>=500)throw new UncertainFailure("TWILIO_RECORDING_PROVIDER_UNAVAILABLE",null);throw new DefiniteFailure("TWILIO_RECORDING_REJECTED_"+response.statusCode(),null);}
        catch(DefiniteFailure|UncertainFailure exception){throw exception;}
        catch(InterruptedException exception){Thread.currentThread().interrupt();throw new UncertainFailure("TWILIO_RECORDING_REQUEST_INTERRUPTED",exception);}
        catch(Exception exception){throw new UncertainFailure("TWILIO_RECORDING_REQUEST_FAILED",exception);}}
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}

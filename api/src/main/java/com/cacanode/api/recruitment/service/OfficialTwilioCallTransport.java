package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.config.RecruitmentCallingProperties;
import com.twilio.exception.ApiException;
import com.twilio.http.HttpMethod;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Call;
import com.twilio.type.PhoneNumber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class OfficialTwilioCallTransport implements TwilioCallTransport {
    private final RecruitmentCallingProperties properties;
    private final TwilioRestClient client;

    public OfficialTwilioCallTransport(RecruitmentCallingProperties properties) {
        this.properties=properties;
        client=new TwilioRestClient.Builder(properties.twilioApiKeySid(),properties.twilioApiKeySecret())
                .accountSid(properties.twilioAccountSid()).build();
    }

    @Override
    public CreatedCall create(CreateCall command) {
        try {
            Call call=Call.creator(new PhoneNumber(command.destination()),new PhoneNumber(properties.twilioFromNumber()),
                            URI.create(command.voiceUrl()))
                    .setMethod(HttpMethod.POST)
                    .setFallbackUrl(URI.create(command.fallbackUrl())).setFallbackMethod(HttpMethod.POST)
                    .setStatusCallback(URI.create(command.statusUrl())).setStatusCallbackMethod(HttpMethod.POST)
                    .setStatusCallbackEvent(List.of("initiated","ringing","answered","completed"))
                    .setTimeout(30).setTimeLimit(command.durationLimitSeconds()+120).setRecord(false)
                    .create(client);
            return new CreatedCall(call.getSid());
        } catch (ApiException exception) {
            Integer status=exception.getStatusCode();
            if(status==null||status>=500)throw new UncertainFailure("TWILIO_CREATE_UNCERTAIN",exception);
            throw new DefiniteFailure("TWILIO_CREATE_REJECTED",exception);
        } catch (RuntimeException exception) {
            throw new UncertainFailure("TWILIO_CREATE_UNCERTAIN",exception);
        }
    }

    @Override
    public boolean terminate(String callSid) {
        try {
            Call.updater(callSid).setStatus(Call.UpdateStatus.COMPLETED).update(client);return true;
        } catch (RuntimeException exception) {return false;}
    }
}

package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.cacanode.api.recruitment.exception.PublicRecruitmentUnavailableException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false} and ${app.recruitment.public.turnstile-enabled:false}")
public class CloudflareTurnstileVerifier implements TurnstileVerifier {
    private final PublicRecruitmentProperties properties;
    private final RestClient client = RestClient.create("https://challenges.cloudflare.com");

    public CloudflareTurnstileVerifier(PublicRecruitmentProperties properties) { this.properties = properties; }

    @Override
    public boolean verify(String token, String remoteIp) {
        if (token == null || token.isBlank()) return false;
        var form = new LinkedMultiValueMap<String,String>();
        form.add("secret", properties.turnstileSecretKey()); form.add("response", token); form.add("remoteip", remoteIp);
        try {
            Response response = client.post().uri("/turnstile/v0/siteverify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Response.class);
            return response != null && response.success();
        } catch (RuntimeException exception) {
            throw new PublicRecruitmentUnavailableException("Application verification is temporarily unavailable", exception);
        }
    }

    private record Response(boolean success, @JsonProperty("error-codes") java.util.List<String> errors) {}
}

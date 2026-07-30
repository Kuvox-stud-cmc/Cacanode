package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
@ConditionalOnExpression("${app.recruitment.enabled:false}")
public class RecruitmentTokenSupport {
    private final PublicRecruitmentProperties properties;
    private final SecureRandom random=new SecureRandom();
    public RecruitmentTokenSupport(PublicRecruitmentProperties properties){this.properties=properties;}
    public String opaqueToken(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    public String hash(String token){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                (properties.tokenPepper()+":"+token).getBytes(StandardCharsets.UTF_8)));}
        catch(Exception exception){throw new IllegalStateException(exception);}
    }
}

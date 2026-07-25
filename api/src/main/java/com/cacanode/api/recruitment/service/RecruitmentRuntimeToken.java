package com.cacanode.api.recruitment.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

final class RecruitmentRuntimeToken {
    private RecruitmentRuntimeToken() {}
    static String derive(String secret,UUID callAttemptId,String snapshotSha256) {
        try {
            Mac hmac=Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] value=hmac.doFinal(("interview-runtime-v1:"+callAttemptId+":"+snapshotSha256)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        } catch(Exception exception) {throw new IllegalStateException("Unable to derive interview runtime token",exception);}
    }
}

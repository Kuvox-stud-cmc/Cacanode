package com.cacanode.api.recruitment.service;

import com.cacanode.api.recruitment.exception.PublicRecruitmentRateLimitException;
import com.cacanode.api.recruitment.exception.PublicRecruitmentUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("${app.recruitment.enabled:false} and ${app.recruitment.public-jobs-enabled:false}")
public class PublicApplicationRateLimiter {
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
            "local c=redis.call('INCR',KEYS[1]); if c==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return c", Long.class);
    private final StringRedisTemplate redis;

    public void requireApplicationAttempt(UUID publicId, String ip) {
        long window = Instant.now().getEpochSecond() / 600;
        String key = "recruitment:apply:" + publicId + ":" + sha256(ip) + ":" + window;
        try {
            Long count = redis.execute(INCREMENT, List.of(key), "600");
            if (count != null && count > 10) throw new PublicRecruitmentRateLimitException(600);
        } catch (PublicRecruitmentRateLimitException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PublicRecruitmentUnavailableException("Application protection is temporarily unavailable", exception);
        }
    }

    public boolean allowEmailDelivery(UUID jobId, String normalizedEmail) {
        long now = Instant.now().getEpochSecond();
        String identity = jobId + ":" + sha256(normalizedEmail);
        try {
            Boolean cooldown = redis.opsForValue().setIfAbsent("recruitment:email:cooldown:" + identity, "1",
                    java.time.Duration.ofSeconds(60));
            if (!Boolean.TRUE.equals(cooldown)) return false;
            String hourKey = "recruitment:email:hour:" + identity + ":" + (now / 3600);
            Long count = redis.execute(INCREMENT, List.of(hourKey), "3600");
            return count != null && count <= 3;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "unknown" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}

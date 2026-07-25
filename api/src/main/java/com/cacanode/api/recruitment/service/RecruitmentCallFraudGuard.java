package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.config.RecruitmentFraudProperties;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix="app.recruitment",name="calling-enabled",havingValue="true")
public class RecruitmentCallFraudGuard {
    private static final DefaultRedisScript<Long> GUARD=new DefaultRedisScript<>("""
            local destination=tonumber(redis.call('GET',KEYS[1]) or '0')
            local tenant=tonumber(redis.call('GET',KEYS[2]) or '0')
            local known=redis.call('SISMEMBER',KEYS[3],ARGV[4])
            local tenants=redis.call('SCARD',KEYS[3])
            if destination >= tonumber(ARGV[1]) then return 1 end
            if tenant >= tonumber(ARGV[2]) then return 2 end
            if known == 0 and tenants >= tonumber(ARGV[3]) then return 3 end
            redis.call('INCR',KEYS[1]); redis.call('EXPIRE',KEYS[1],ARGV[5])
            redis.call('INCR',KEYS[2]); redis.call('EXPIRE',KEYS[2],ARGV[5])
            redis.call('SADD',KEYS[3],ARGV[4]); redis.call('EXPIRE',KEYS[3],ARGV[5])
            return 0
            """,Long.class);
    private final StringRedisTemplate redis;
    private final RecruitmentFraudProperties properties;
    private final MeterRegistry metrics;

    public String requireAttempt(UUID tenantId,String rawDestination) {
        String destination=canonicalVietnameseDestination(rawDestination);
        String day=LocalDate.now(ZoneOffset.UTC).toString();
        String destinationKey=fingerprint("destination:"+destination);
        String tenantKey=fingerprint("tenant:"+tenantId);
        try {
            Long result=redis.execute(GUARD,List.of("recruitment:fraud:destination:"+day+":"+destinationKey,
                    "recruitment:fraud:tenant:"+day+":"+tenantKey,
                    "recruitment:fraud:tenants:"+day+":"+destinationKey),
                    Integer.toString(properties.destinationAttemptsPerDay()),
                    Integer.toString(properties.tenantAttemptsPerDay()),
                    Integer.toString(properties.destinationTenantLimit()),tenantKey,"172800");
            if(result==null)throw rejected("GUARD_UNAVAILABLE");
            if(result==1)throw rejected("DESTINATION_DAILY_LIMIT");
            if(result==2)throw rejected("TENANT_DAILY_LIMIT");
            if(result==3)throw rejected("DESTINATION_TENANT_LIMIT");
            metrics.counter("recruitment.call.fraud_guard","outcome","allowed").increment();
            return destination;
        } catch(ConflictException exception){throw exception;}
        catch(RuntimeException exception){throw rejected("GUARD_UNAVAILABLE");}
    }

    String canonicalVietnameseDestination(String raw) {
        try {
            PhoneNumberUtil util=PhoneNumberUtil.getInstance();PhoneNumber number=util.parse(raw,"VN");
            if(number.getCountryCode()!=84||!util.isValidNumberForRegion(number,"VN"))throw rejected("INVALID_DESTINATION");
            PhoneNumberUtil.PhoneNumberType type=util.getNumberType(number);
            if(type==PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE||type==PhoneNumberUtil.PhoneNumberType.SHARED_COST
                    ||type==PhoneNumberUtil.PhoneNumberType.VOIP||type==PhoneNumberUtil.PhoneNumberType.UNKNOWN)
                throw rejected("HIGH_RISK_DESTINATION");
            return util.format(number,PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch(ConflictException exception){throw exception;}
        catch(Exception exception){throw rejected("INVALID_DESTINATION");}
    }

    private ConflictException rejected(String reason){metrics.counter("recruitment.call.fraud_guard","outcome","rejected","reason",reason).increment();return new ConflictException(reason);}
    private String fingerprint(String value){
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(properties.fingerprintSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception exception){throw new IllegalStateException(exception);}
    }
}

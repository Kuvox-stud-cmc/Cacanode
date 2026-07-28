package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.config.PublicRecruitmentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicJobCursorCodecTest {
    private final PublicRecruitmentProperties properties=new PublicRecruitmentProperties(
            "pepper","cursor-secret","http://localhost/applications/manage",false,
            false,"","",false,"localhost",3310,5242880);
    private final ObjectMapper mapper=new ObjectMapper().findAndRegisterModules();
    private final Clock now=Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC);

    @Test void encryptsAuthenticatesAndBindsCursorToFiltersAndSort(){
        PublicJobCursorCodec codec=new PublicJobCursorCodec(properties,mapper,now);
        String fingerprint=codec.fingerprint(Map.of("q","java","sort","relevance"));
        UUID id=UUID.randomUUID();String encoded=codec.encode("relevance","1.25",id,fingerprint);
        var decoded=codec.decode(encoded,"relevance",fingerprint);
        assertEquals(id,decoded.publicId());assertEquals("1.25",decoded.value());
        assertThrows(BadRequestException.class,()->codec.decode(encoded,"newest",fingerprint));
        assertThrows(BadRequestException.class,()->codec.decode(encoded,"relevance",codec.fingerprint(Map.of("q","go"))));
        char replacement=encoded.charAt(encoded.length()-1)=='A'?'B':'A';
        String tampered=encoded.substring(0,encoded.length()-1)+replacement;
        assertThrows(BadRequestException.class,()->codec.decode(tampered,"relevance",fingerprint));
    }

    @Test void rejectsExpiredCursor(){
        PublicJobCursorCodec encoder=new PublicJobCursorCodec(properties,mapper,now);
        String fingerprint=encoder.fingerprint(Map.of("sort","newest"));
        String encoded=encoder.encode("newest","2026-07-24T00:00:00",UUID.randomUUID(),fingerprint);
        PublicJobCursorCodec later=new PublicJobCursorCodec(properties,mapper,
                Clock.fixed(Instant.parse("2026-07-24T02:00:00Z"),ZoneOffset.UTC));
        assertThrows(BadRequestException.class,()->later.decode(encoded,"newest",fingerprint));
    }
}

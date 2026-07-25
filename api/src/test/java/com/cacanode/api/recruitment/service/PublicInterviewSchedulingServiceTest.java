package com.cacanode.api.recruitment.service;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PublicInterviewSchedulingServiceTest {
    @Test void omitsDstGapsAndReturnsBothOverlapInstants(){
        ZoneId berlin=ZoneId.of("Europe/Berlin");
        assertTrue(PublicInterviewSchedulingService.resolveLocal(LocalDateTime.of(2026,3,29,2,30),berlin).isEmpty());
        var overlap=PublicInterviewSchedulingService.resolveLocal(LocalDateTime.of(2026,10,25,2,30),berlin);
        assertEquals(2,overlap.size());assertNotEquals(overlap.get(0),overlap.get(1));
    }

    @Test void ordinaryZonesResolveOneInstant(){
        assertEquals(List.of(Instant.parse("2026-07-24T02:00:00Z")),PublicInterviewSchedulingService.resolveLocal(
                LocalDateTime.of(2026,7,24,9,0),ZoneId.of("Asia/Ho_Chi_Minh")));
    }
}

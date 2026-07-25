package com.cacanode.api.recruitment;

import org.junit.jupiter.api.Test;

import com.cacanode.api.recruitment.controller.RecruitmentController;
import com.cacanode.api.recruitment.query.RecruitmentQueryService;
import com.cacanode.api.recruitment.service.RecruitmentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RecruitmentDormancyTest {
    @Test
    void phaseThreeRuntimeComponentsShareTheMasterFeatureBoundary() {
        for (Class<?> type : new Class<?>[]{RecruitmentController.class, RecruitmentService.class,
                RecruitmentQueryService.class}) {
            ConditionalOnProperty condition = type.getAnnotation(ConditionalOnProperty.class);
            assertNotNull(condition, type.getName());
            assertEquals("app.recruitment", condition.prefix());
            assertEquals("enabled", condition.name()[0]);
            assertEquals("true", condition.havingValue());
        }
    }
}

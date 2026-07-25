package com.cacanode.api.recruitment;

import com.cacanode.api.notification.listener.CandidateAccessEmailListener;
import com.cacanode.api.recruitment.controller.*;
import com.cacanode.api.recruitment.query.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import static org.junit.jupiter.api.Assertions.*;

class PublicRecruitmentDormancyTest {
    @Test void publicRuntimeRequiresBothRecruitmentFlags(){
        for(Class<?> type:new Class<?>[]{PublicJobController.class,CandidateAccessController.class,
                RecruitmentCvController.class,PublicJobQueryService.class,PublicApplicationService.class,
                CandidateAccessService.class,RecruitmentCvStorageService.class,PublicJobProjectionService.class,
                CandidateAccessEmailListener.class}){
            ConditionalOnExpression condition=type.getAnnotation(ConditionalOnExpression.class);
            assertNotNull(condition,type.getName());
            assertTrue(condition.value().contains("app.recruitment.enabled"),type.getName());
            assertTrue(condition.value().contains("app.recruitment.public-jobs-enabled"),type.getName());
        }
    }
}

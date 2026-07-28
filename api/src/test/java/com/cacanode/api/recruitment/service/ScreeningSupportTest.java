package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScreeningSupportTest {
    private final ScreeningSupport support=new ScreeningSupport(new ObjectMapper().findAndRegisterModules());

    @Test void validatesAnswersEvaluatesAllCriteriaAndHidesAcceptedOptions(){
        UUID question=UUID.randomUUID(),yes=UUID.randomUUID(),no=UUID.randomUUID();
        String config=support.validateAndWrite(List.of(new RecruitmentDtos.ScreeningQuestion(question,"Can work weekends?",
                List.of(new RecruitmentDtos.ScreeningOption(yes,"Yes"),new RecruitmentDtos.ScreeningOption(no,"No")),List.of(yes))));
        String answers=support.validateAnswers(config,List.of(new PublicRecruitmentDtos.ScreeningAnswer(question,yes)));
        assertTrue(support.matches(config,answers));
        assertFalse(support.publicJson(config).contains("acceptedOptionIds"));
        assertThrows(BadRequestException.class,()->support.validateAnswers(config,List.of()));
    }
}

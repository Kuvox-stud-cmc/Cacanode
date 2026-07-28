package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplateSnapshotSupportTest {
    private final TemplateSnapshotSupport support = new TemplateSnapshotSupport(new ObjectMapper());

    @Test
    void canonicalHashIsDeterministicAndRoundTrips() {
        var content = englishContent();
        var first = support.validateAndCreate("en-US", content);
        var second = support.validateAndCreate("en-US", content);
        assertEquals(first.json(), second.json());
        assertEquals(first.sha256(), second.sha256());
        assertEquals(64, first.sha256().length());
        assertEquals(content, support.read(first.json()));
    }

    @Test
    void rejectsNonContiguousAndInvalidEnglishScreening() {
        var question = new RecruitmentDtos.Question(UUID.randomUUID(), 1, "Question", "Skill", "Rubric", 0,
                InterviewInferenceApi.QuestionSource.TEMPLATE, null);
        var badPosition = new RecruitmentDtos.Section(UUID.randomUUID(), 2, InterviewInferenceApi.SectionKind.CORE, "en-US", 60, null, List.of(question));
        assertThrows(BadRequestException.class, () -> support.validateAndCreate("en-US",
                content(List.of(badPosition), 120)));

        var englishScreen = new RecruitmentDtos.Section(UUID.randomUUID(), 1, InterviewInferenceApi.SectionKind.ENGLISH_SCREEN, "en-US",
                301, "Now in English", List.of(question));
        assertThrows(BadRequestException.class, () -> support.validateAndCreate("vi-VN",
                content(List.of(englishScreen), 400)));
    }

    @Test
    void enforcesTheConfiguredProviderDurationCeiling() {
        var trialSupport=new TemplateSnapshotSupport(new ObjectMapper(),600);
        assertDoesNotThrow(()->trialSupport.validateAndCreate("en-US",content(englishContent().sections(),600)));
        BadRequestException error=assertThrows(BadRequestException.class,
                ()->trialSupport.validateAndCreate("en-US",content(englishContent().sections(),601)));
        assertEquals("Interview duration cannot exceed 600 seconds",error.getMessage());
    }

    private static RecruitmentDtos.RevisionContent englishContent() {
        var q = new RecruitmentDtos.Question(UUID.randomUUID(), 1, "Tell me about your work", "Communication", "Clear answer", 1,
                InterviewInferenceApi.QuestionSource.TEMPLATE, null);
        var section = new RecruitmentDtos.Section(UUID.randomUUID(), 1, InterviewInferenceApi.SectionKind.CORE, "en-US", 120, null, List.of(q));
        return content(List.of(section), 180);
    }

    private static RecruitmentDtos.RevisionContent content(List<RecruitmentDtos.Section> sections, int duration) {
        return new RecruitmentDtos.RevisionContent("Introduction", "Disclosure", "Closing", duration,
                new RecruitmentDtos.InteractionLimits(1, 1, 10, 1), sections);
    }
}

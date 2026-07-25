package com.cacanode.api.recruitment.service;

import com.cacanode.api.ai.api.InterviewInferenceApi;
import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class TemplateSnapshotSupport {
    private final ObjectMapper mapper;

    TemplateSnapshotSupport(ObjectMapper source) {
        this.mapper = source.copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    Snapshot validateAndCreate(String locale, RecruitmentDtos.RevisionContent content) {
        if (content == null) throw new BadRequestException("Template content is required");
        if (blank(content.introductionText()) || blank(content.disclosureText())
                || blank(content.closingText()) || content.durationLimitSeconds() <= 0) {
            throw new BadRequestException("Template texts and a positive duration are required");
        }
        RecruitmentDtos.InteractionLimits limits = content.interactionLimits();
        if (limits == null || limits.repetitionLimit() < 0 || limits.clarificationLimit() < 0
                || limits.silenceTimeoutSeconds() <= 0 || limits.silencePromptLimit() < 0) {
            throw new BadRequestException("Interaction limits are invalid");
        }
        List<RecruitmentDtos.Section> sections = content.sections();
        if (sections == null || sections.isEmpty()) throw new BadRequestException("At least one section is required");
        requireContiguous(sections.stream().map(RecruitmentDtos.Section::position).toList(), "section");
        Set<UUID> sectionIds = new HashSet<>();
        Set<UUID> questionIds = new HashSet<>();
        int englishScreens = 0;
        int totalDuration = 0;
        for (int index = 0; index < sections.size(); index++) {
            RecruitmentDtos.Section section = sections.get(index);
            if (section.sectionId() == null || section.kind() == null || section.durationLimitSeconds() <= 0
                    || section.questions() == null || section.questions().isEmpty()) {
                throw new BadRequestException("Every section requires an ID, kind, positive duration, and questions");
            }
            if (!sectionIds.add(section.sectionId())) throw new BadRequestException("Section IDs must be unique");
            requireContiguous(section.questions().stream().map(RecruitmentDtos.Question::position).toList(), "question");
            for (RecruitmentDtos.Question question : section.questions()) {
                if (question.questionId() == null || blank(question.prompt()) || blank(question.competency())
                        || blank(question.rubric()) || question.followUpLimit() < 0 || question.source() == null) {
                    throw new BadRequestException("Every question requires complete valid content");
                }
                if (!questionIds.add(question.questionId())) throw new BadRequestException("Question IDs must be unique");
            }
            totalDuration += section.durationLimitSeconds();
            boolean englishScreen = InterviewInferenceApi.SectionKind.ENGLISH_SCREEN == section.kind();
            boolean core = InterviewInferenceApi.SectionKind.CORE == section.kind();
            if ("en-US".equals(locale)) {
                if (!core || !"en-US".equals(section.languageTag())) {
                    throw new BadRequestException("English templates may contain only English CORE sections");
                }
            } else if (englishScreen) {
                englishScreens++;
                if (!"en-US".equals(section.languageTag()) || section.questions().size() < 2
                        || section.questions().size() > 5 || section.durationLimitSeconds() > 300) {
                    throw new BadRequestException("Vietnamese English screening must contain 2-5 English questions and last at most 300 seconds");
                }
                if (blank(section.transitionText())) {
                    throw new BadRequestException("English screening requires a spoken transition");
                }
                if (index + 1 < sections.size() && blank(sections.get(index + 1).transitionText())) {
                    throw new BadRequestException("The following Vietnamese section requires a transition");
                }
            } else if (!"vi-VN".equals(section.languageTag())) {
                throw new BadRequestException("Vietnamese CORE sections must use vi-VN");
            }
        }
        if (englishScreens > 1) throw new BadRequestException("Only one English screening section is allowed");
        if (totalDuration > content.durationLimitSeconds()) {
            throw new BadRequestException("Section durations exceed the interview duration");
        }
        try {
            String json = mapper.writeValueAsString(content);
            return new Snapshot(json, sha256(json));
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Template content is invalid");
        }
    }

    RecruitmentDtos.RevisionContent read(String json) {
        try {
            return mapper.readValue(json, RecruitmentDtos.RevisionContent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored template snapshot is invalid", exception);
        }
    }

    private static void requireContiguous(List<Integer> positions, String kind) {
        for (int index = 0; index < positions.size(); index++) {
            if (positions.get(index) == null || positions.get(index) != index + 1) {
                throw new BadRequestException("Template " + kind + " positions must be contiguous from 1");
            }
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    record Snapshot(String json, String sha256) {}
}

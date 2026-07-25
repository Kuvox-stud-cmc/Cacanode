package com.cacanode.api.recruitment.service;

import com.cacanode.api.common.exception.custom.BadRequestException;
import com.cacanode.api.common.exception.custom.ConflictException;
import com.cacanode.api.recruitment.dto.PublicRecruitmentDtos;
import com.cacanode.api.recruitment.dto.RecruitmentDtos;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ScreeningSupport {
    private final ObjectMapper objectMapper;

    public String validateAndWrite(List<RecruitmentDtos.ScreeningQuestion> questions) {
        List<RecruitmentDtos.ScreeningQuestion> value = questions == null ? List.of() : List.copyOf(questions);
        if (value.size() > 10) throw new BadRequestException("At most ten screening questions are allowed");
        Set<UUID> questionIds = new HashSet<>();
        for (var question : value) {
            if (question == null || question.questionId() == null || !questionIds.add(question.questionId()))
                throw new BadRequestException("Screening question IDs must be unique");
            if (question.prompt() == null || question.prompt().isBlank())
                throw new BadRequestException("Screening prompts cannot be blank");
            if (question.options() == null || question.options().size() < 2 || question.options().size() > 10)
                throw new BadRequestException("Each screening question requires two to ten options");
            Set<UUID> optionIds = new HashSet<>();
            for (var option : question.options()) {
                if (option == null || option.optionId() == null || !optionIds.add(option.optionId())
                    || option.label() == null || option.label().isBlank())
                    throw new BadRequestException("Screening option IDs and labels must be valid and unique");
            }
            if (question.acceptedOptionIds() == null || question.acceptedOptionIds().isEmpty()
                    || !optionIds.containsAll(question.acceptedOptionIds())
                    || new HashSet<>(question.acceptedOptionIds()).size() != question.acceptedOptionIds().size())
                throw new BadRequestException("Accepted screening options must reference unique configured options");
        }
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new BadRequestException("Screening configuration is invalid"); }
    }

    public List<RecruitmentDtos.ScreeningQuestion> read(String json) {
        try { return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Stored screening configuration is invalid", e); }
    }

    public String validateAnswers(String configJson,List<PublicRecruitmentDtos.ScreeningAnswer> answers) {
        var questions = read(configJson);
        List<PublicRecruitmentDtos.ScreeningAnswer> supplied = answers == null ? List.of() : List.copyOf(answers);
        if (supplied.size() != questions.size()) throw new BadRequestException("Every screening question must be answered");
        Map<UUID,UUID> byQuestion = new HashMap<>();
        for (var answer : supplied) {
            if (answer == null || answer.questionId() == null || answer.optionId() == null
                    || byQuestion.put(answer.questionId(),answer.optionId()) != null)
                throw new BadRequestException("Screening answers must be unique and complete");
        }
        for (var question : questions) {
            UUID selected = byQuestion.get(question.questionId());
            if (selected == null || question.options().stream().noneMatch(o -> o.optionId().equals(selected)))
                throw new BadRequestException("A screening answer does not belong to its question");
        }
        try { return objectMapper.writeValueAsString(supplied); }
        catch (Exception e) { throw new BadRequestException("Screening answers are invalid"); }
    }

    public boolean matches(String configJson,String answersJson) {
        var questions = read(configJson);
        try {
            List<PublicRecruitmentDtos.ScreeningAnswer> answers = objectMapper.readValue(answersJson,new TypeReference<>(){});
            Map<UUID,UUID> selected = new HashMap<>();
            answers.forEach(a -> selected.put(a.questionId(),a.optionId()));
            return !questions.isEmpty() && questions.stream().allMatch(q -> q.acceptedOptionIds().contains(selected.get(q.questionId())));
        } catch (Exception e) { return false; }
    }

    public List<PublicRecruitmentDtos.PublicScreeningQuestion> publicQuestions(String configJson) {
        return read(configJson).stream().map(q -> new PublicRecruitmentDtos.PublicScreeningQuestion(
                q.questionId(),q.prompt(),q.options().stream()
                .map(o -> new PublicRecruitmentDtos.PublicScreeningOption(o.optionId(),o.label())).toList())).toList();
    }

    public String publicJson(String configJson) {
        try { return objectMapper.writeValueAsString(publicQuestions(configJson)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public void requireMatchingCriteria(String configJson) {
        if (read(configJson).isEmpty()) throw new ConflictException("Matching automation requires at least one screening criterion");
    }
}

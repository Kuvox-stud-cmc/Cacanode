package com.cacanode.api.recruitment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class InterviewResultDtos {
    private InterviewResultDtos() {}
    public static final String ENGLISH_WARNING="This workplace English screen is advisory and is not IELTS or formal CEFR certification.";

    public record Turn(UUID turnId,int sequence,String speaker,String turnKind,UUID sectionId,UUID questionId,
            String languageTag,long startedAtEpochMs,long endedAtEpochMs,String transcript,boolean interrupted){}
    public record Transcript(UUID interviewId,String deliveryStatus,int expectedTurnCount,int persistedTurnCount,
            int page,int size,List<Turn> turns){}
    public record EnglishDimensions(BigDecimal comprehension,BigDecimal fluency,BigDecimal vocabulary,
            BigDecimal grammar,BigDecimal pronunciation){}
    public record Evaluation(UUID candidateTurnId,boolean accepted,BigDecimal rubricScore,
            EnglishDimensions englishDimensions){}
    public record QuestionResult(UUID questionId,String sectionKind,String status,BigDecimal score,
            List<UUID> evidenceTurnIds,List<Evaluation> evaluations){}
    public record SectionResult(UUID sectionId,String kind,String status,List<QuestionResult> questions){}
    public record Result(UUID interviewId,String terminalKind,String deliveryStatus,String completionReason,
            String failureCode,Boolean retryable,String failureDetail,boolean partial,int expectedTurnCount,
            int persistedTurnCount,int connectedSeconds,String scorePolicyVersion,BigDecimal overallScore,
            EnglishDimensions englishDimensions,String englishBand,boolean advisoryOnly,String englishWarning,
            OffsetDateTime occurredAt,List<SectionResult> sections){}
    public record Recording(UUID recordingId,String state,String contentType,Long sizeBytes,
            OffsetDateTime retainedUntil,OffsetDateTime readyAt,OffsetDateTime deletedAt){}
}

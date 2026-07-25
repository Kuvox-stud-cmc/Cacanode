from __future__ import annotations

import grpc

from app.generated import cacanode_ai_v1_pb2 as pb
from app.modules.interview.api import (
    CancelInterviewCommand,
    InteractionLimits,
    InterviewDisabledError,
    InterviewQuestionSnapshot,
    InterviewQuestionSource,
    InterviewRuntimeApi,
    InterviewRuntimeConflictError,
    InterviewRuntimeNotReadyError,
    InterviewRuntimeValidationError,
    InterviewSectionKind,
    InterviewSectionSnapshot,
    PrepareInterviewCommand,
)


class InterviewGrpcHandler:
    def __init__(self, runtime: InterviewRuntimeApi) -> None:
        self._runtime = runtime

    async def prepare(
        self, request: pb.PrepareInterviewSessionRequest, context: grpc.aio.ServicerContext
    ) -> pb.PrepareInterviewSessionResponse:
        try:
            result = await self._runtime.prepare(_prepare_command(request))
        except InterviewDisabledError:
            await context.abort(grpc.StatusCode.FAILED_PRECONDITION, "INTERVIEW_DISABLED")
        except InterviewRuntimeNotReadyError:
            await context.abort(grpc.StatusCode.UNAVAILABLE, "INTERVIEW_RUNTIME_NOT_READY")
        except InterviewRuntimeConflictError:
            await context.abort(grpc.StatusCode.ALREADY_EXISTS, "INTERVIEW_PREPARATION_CONFLICT")
        except InterviewRuntimeValidationError as exception:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exception))
        return pb.PrepareInterviewSessionResponse(
            session_id=result.session_id,
            call_attempt_id=result.call_attempt_id,
            runtime_token=result.runtime_token,
            expires_at_epoch_seconds=result.expires_at_epoch_seconds,
            accepted_snapshot_sha256=result.accepted_snapshot_sha256,
        )

    async def cancel(
        self, request: pb.CancelInterviewSessionRequest, context: grpc.aio.ServicerContext
    ) -> pb.CancelInterviewSessionResponse:
        try:
            result = await self._runtime.cancel(
                CancelInterviewCommand(
                    session_id=request.session_id,
                    call_attempt_id=request.call_attempt_id,
                    reason=request.reason,
                )
            )
        except InterviewDisabledError:
            await context.abort(grpc.StatusCode.FAILED_PRECONDITION, "INTERVIEW_DISABLED")
        except InterviewRuntimeNotReadyError:
            await context.abort(grpc.StatusCode.UNAVAILABLE, "INTERVIEW_RUNTIME_NOT_READY")
        except InterviewRuntimeConflictError:
            await context.abort(
                grpc.StatusCode.FAILED_PRECONDITION, "INTERVIEW_CANCELLATION_CONFLICT"
            )
        except InterviewRuntimeValidationError as exception:
            await context.abort(grpc.StatusCode.INVALID_ARGUMENT, str(exception))
        return pb.CancelInterviewSessionResponse(
            session_id=result.session_id,
            call_attempt_id=result.call_attempt_id,
            cancelled=result.cancelled,
            already_terminal=result.already_terminal,
        )


def _prepare_command(request: pb.PrepareInterviewSessionRequest) -> PrepareInterviewCommand:
    limits = request.interaction_limits
    return PrepareInterviewCommand(
        session_id=request.session_id,
        call_attempt_id=request.call_attempt_id,
        tenant_id=request.tenant_id,
        template_revision_id=request.template_revision_id,
        snapshot_version=request.snapshot_version,
        snapshot_sha256=request.snapshot_sha256,
        company_display_name=request.company_display_name,
        candidate_display_name=request.candidate_display_name,
        introduction_text=request.introduction_text,
        disclosure_text=request.disclosure_text,
        closing_text=request.closing_text,
        duration_limit_seconds=request.duration_limit_seconds,
        interaction_limits=InteractionLimits(
            repetition_limit=limits.repetition_limit,
            clarification_limit=limits.clarification_limit,
            silence_timeout_seconds=limits.silence_timeout_seconds,
            silence_prompt_limit=limits.silence_prompt_limit,
        ),
        recording_enabled=request.recording_enabled,
        cv_personalization_enabled=request.cv_personalization_enabled,
        sections=tuple(_section(section) for section in request.sections),
    )


def _section(section: pb.InterviewSectionSnapshot) -> InterviewSectionSnapshot:
    kind = (
        InterviewSectionKind.ENGLISH_SCREEN
        if section.kind == pb.INTERVIEW_SECTION_KIND_ENGLISH_SCREEN
        else InterviewSectionKind.CORE
    )
    return InterviewSectionSnapshot(
        section_id=section.section_id,
        position=section.position,
        kind=kind,
        language_tag=section.language_tag,
        duration_limit_seconds=section.duration_limit_seconds,
        transition_text=section.transition_text,
        questions=tuple(_question(question) for question in section.questions),
    )


def _question(question: pb.InterviewQuestionSnapshot) -> InterviewQuestionSnapshot:
    source = (
        InterviewQuestionSource.CV_PERSONALIZED
        if question.source == pb.INTERVIEW_QUESTION_SOURCE_CV_PERSONALIZED
        else InterviewQuestionSource.TEMPLATE
    )
    return InterviewQuestionSnapshot(
        question_id=question.question_id,
        position=question.position,
        prompt=question.prompt,
        competency=question.competency,
        rubric=question.rubric,
        follow_up_limit=question.follow_up_limit,
        source=source,
        evidence=question.evidence if question.HasField("evidence") else None,
    )
